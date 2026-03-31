package com.nexusiam.application.service.authentication;

import com.nexusiam.application.dto.response.LoginResponse;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface SSOExchangeService {
    Map<String, String> requestAuthCode(String state);
    LoginResponse exchangeAuthCode(String authCode, String state, String ipAddress, String userAgent, HttpServletRequest request);
    Map<String, Object> getProfile(String accessToken);
    LoginResponse refreshAccessToken(String refreshToken, String ipAddress, String userAgent);
    Map<String, Object> introspectToken(String token);
}
