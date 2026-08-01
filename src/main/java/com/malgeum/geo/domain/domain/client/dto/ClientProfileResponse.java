package com.malgeum.geo.domain.domain.client.dto;

import com.malgeum.geo.domain.domain.client.entity.Client;

public record ClientProfileResponse(String email, String name, String phone, String company, String plan) {
    public static ClientProfileResponse from(Client client){
        String phoneStr = client.getPhone()==null?"미입력":client.getPhone();
        String companyStr = client.getCompany()==null?"미입력":client.getCompany();
        String planStr = client.getPlan().name();
        return new ClientProfileResponse(client.getEmail(),client.getName(),phoneStr,companyStr,planStr);
    }
}
