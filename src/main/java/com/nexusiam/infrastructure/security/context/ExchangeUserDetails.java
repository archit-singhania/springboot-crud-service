package com.nexusiam.infrastructure.security.context;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ExchangeUserDetails {
    private String profileId;
    private String grpId;
    private Map<String, Object> registrations;
    private String currentRole;
    private Object roles;
}
