package com.malgeum.geo.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.domain.domain.Client.OAuthProvider;
import com.malgeum.geo.global.common.ClientRepository;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpSession;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final HttpSession httpSession;
    private final ClientRepository clientRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // 어떤 Request Server를 이용하고 있는지 구분
        // 구글 로그인인지, 네이버인지, 카카오인지..
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if (!registrationId.equals("google")) {
            throw new OAuth2AuthenticationException("지원하지 않는 OAuth provider입니다.");
        }

        Map<String, Object> attributes = oAuth2User.getAttributes();

        Client client = this.login(registrationId, attributes);

        httpSession.setAttribute("clientId", client.getId());
        httpSession.setAttribute("email", client.getEmail());

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub");
    }

    private Client login(String registrationId, Map<String, Object> attributes) {
        if (registrationId.equals("google")) {
            String name = (String) attributes.get("name");
            String email = (String) attributes.get("email");
            String providerId = (String) attributes.get("sub");
            Client client = clientRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, providerId)
                    .orElseGet(() -> createGoogleClient(email, name, providerId));
            return client;
        }
        throw new OAuth2AuthenticationException("지원하지 않는 OAuth provider입니다.");
    }

    private Client createGoogleClient(String email, String name, String providerId) {
        Optional<Client> existingClient = clientRepository.findByEmail(email);

        if (existingClient.isPresent()) {
            Client client = existingClient.get();
            client.linkOAuth(OAuthProvider.GOOGLE, providerId);
            return clientRepository.save(client);
        }

        Client client = Client.builder()
                .email(email)
                .name(name)
                .password(null)
                .phone(null)
                .company(null)
                .provider(OAuthProvider.GOOGLE)
                .providerId(providerId)
                .build();

        return clientRepository.save(client);
    }
}
