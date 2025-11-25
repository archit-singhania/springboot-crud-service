package com.example.demo.service;

import com.example.demo.dto.LoginResponse;
import com.example.demo.model.SSOExchangeMaster;

import java.util.Map;

public interface SSOExchangeService {
    Map<String, String> requestAuthCode(String state);
    LoginResponse exchangeAuthCode(String authCode, String state);
    SSOExchangeMaster getProfile(String accessToken);
    LoginResponse refreshAccessToken(String refreshToken);
    SSOExchangeMaster getOrganizationProfile(String orgId, String serviceToken);
}