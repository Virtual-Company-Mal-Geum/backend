package com.malgeum.geo.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.domain.domain.OAuthAccount;
import jdk.jshell.spi.ExecutionControl;

import jakarta.servlet.http.HttpSession;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User>{
    private final ClientService clientService;
	private final HttpSession httpSession;

    @Autowired
    public CustomOAuth2UserService(ClientService clientService, HttpSession httpSession) {
        this.clientService = clientService;
        this.httpSession = httpSession;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        
        // 어떤 Request Server를 이용하고 있는지 구분
		// 구글 로그인인지, 네이버인지, 카카오인지..
		String registrationId = userRequest.getClientRegistration().getRegistrationId();
		Map<String, Object> attributes = oAuth2User.getAttributes();
		Client client;
		try {
			client=this.login(registrationId,attributes);
		} catch(ExecutionControl.NotImplementedException e) {
			throw new RuntimeException(e);
		}

        httpSession.setAttribute("client", new OAuthAccount(client));

        return oAuth2User;
    }

    private Client login(String registrationId, Map<String, Object> attributes) throws ExecutionControl.NotImplementedException{
        if(registrationId.startsWith("google")
			|| registrationId.startsWith("kakao")
			|| registrationId.startsWith("naver")){
            String name = (String) attributes.get("name");
            String email = (String) attributes.get("email");
            return this.clientService.socialLogin(name, email);
        }
        throw new ExecutionControl.NotImplementedException("Unsupported registrationId: " + registrationId);
    }

    class CustomOAuth2User extends Client implements OAuth2User{
		public CustomOAuth2User(String password, String name, String company, String email, String phone) {
			super(password, email, name, company, phone);
		}
		
		@Override
		public Map<String,Object> getAttributes(){
			return null;
		}
		
		@Override
		public String getName() {
			return this.getName();
		}
		
		@Override
		public Collection<? extends GrantedAuthority> getAuthorities(){
			List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            return authorities;
		}
	}
}
