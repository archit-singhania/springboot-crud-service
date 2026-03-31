package com.nexusiam.application.service.utility;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@Slf4j
public class DeviceFingerprintService {

    public String generateDeviceFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String acceptLanguage = request.getHeader("Accept-Language");
        String acceptEncoding = request.getHeader("Accept-Encoding");
        String ipAddress = getClientIP(request);

        String raw = String.format("%s|%s|%s|%s",
            userAgent != null ? userAgent : "",
            acceptLanguage != null ? acceptLanguage : "",
            acceptEncoding != null ? acceptEncoding : "",
            ipAddress);

        return hashString(raw);
    }

    public String generateBrowserFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String secChUa = request.getHeader("sec-ch-ua");
        String secChUaPlatform = request.getHeader("sec-ch-ua-platform");

        String raw = String.format("%s|%s|%s",
            userAgent != null ? userAgent : "",
            secChUa != null ? secChUa : "",
            secChUaPlatform != null ? secChUaPlatform : "");

        return hashString(raw);
    }

    public String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "";
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to hash fingerprint", e);
            return input;
        }
    }
}
