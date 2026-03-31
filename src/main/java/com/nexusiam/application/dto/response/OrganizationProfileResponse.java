package com.nexusiam.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationProfileResponse {
    private String name;
    private String cin;
    private String pan;
    private String grpId;
    private List<String> roles;
    private String authorizedPerson;
    private List<UnitInfo> units;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnitInfo {
        private String id;
        private String name;
        private String type;
        private String gstin;
        private String registrationType;
        private String address;
        private List<String> roles;
        private String authorizedPerson;
        private String phone;
        private String email;
    }
}
