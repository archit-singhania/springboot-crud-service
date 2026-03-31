package com.nexusiam.application.service.utility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserAgentParserService {

    public ParsedUserAgent parse(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return new ParsedUserAgent("UNKNOWN", "UNKNOWN", "UNKNOWN");
        }

        String deviceType = detectDeviceType(userAgent);
        String browserName = detectBrowser(userAgent);
        String osName = detectOS(userAgent);

        return new ParsedUserAgent(deviceType, browserName, osName);
    }

    private String detectDeviceType(String ua) {
        if (ua.contains("Mobile") || ua.contains("Android") || ua.contains("iPhone")) {
            return "MOBILE";
        } else if (ua.contains("Tablet") || ua.contains("iPad")) {
            return "TABLET";
        }
        return "DESKTOP";
    }

    private String detectBrowser(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/") && !ua.contains("Chrome")) return "Safari";
        if (ua.contains("OPR/") || ua.contains("Opera/")) return "Opera";
        return "Unknown";
    }

    private String detectOS(String ua) {
        if (ua.contains("Windows NT 10.0")) return "Windows 10/11";
        if (ua.contains("Windows NT 6.3")) return "Windows 8.1";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return "Unknown";
    }

    @Data
    @AllArgsConstructor
    public static class ParsedUserAgent {
        private String deviceType;
        private String browserName;
        private String osName;
    }
}
