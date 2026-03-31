package com.nexusiam.application.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusiam.application.dto.RegistrationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationService {

    private final ObjectMapper objectMapper;

    public List<RegistrationDTO> parseRegistrations(JsonNode registrationsJson) {
        List<RegistrationDTO> registrations = new ArrayList<>();

        if (registrationsJson == null || !registrationsJson.isArray()) {
            return registrations;
        }

        for (JsonNode node : registrationsJson) {
            try {
                RegistrationDTO dto = objectMapper.treeToValue(node, RegistrationDTO.class);
                registrations.add(dto);
            } catch (JsonProcessingException e) {
                log.error("Error parsing registration node: {}", node, e);
            }
        }

        return registrations;
    }

    public JsonNode toJsonNode(List<RegistrationDTO> registrations) {
        try {
            return objectMapper.valueToTree(registrations);
        } catch (Exception e) {
            log.error("Error converting registrations to JSON", e);
            return objectMapper.createArrayNode();
        }
    }

    public JsonNode addOrUpdateRegistration(JsonNode existingRegistrations, RegistrationDTO newRegistration) {
        List<RegistrationDTO> registrations = parseRegistrations(existingRegistrations);

        registrations.removeIf(r ->
            r.getPortalId().equals(newRegistration.getPortalId()) &&
            r.getUnitId().equals(newRegistration.getUnitId())
        );

        registrations.add(newRegistration);

        return toJsonNode(registrations);
    }

    public JsonNode removeRegistration(JsonNode existingRegistrations, String portalId, String unitId) {
        List<RegistrationDTO> registrations = parseRegistrations(existingRegistrations);

        registrations.removeIf(r ->
            r.getPortalId().equals(portalId) &&
            r.getUnitId().equals(unitId)
        );

        return toJsonNode(registrations);
    }

    public List<RegistrationDTO> getActiveRegistrations(JsonNode registrationsJson) {
        List<RegistrationDTO> allRegistrations = parseRegistrations(registrationsJson);

        return allRegistrations.stream()
            .filter(r -> "Active".equalsIgnoreCase(r.getStatus()))
            .toList();
    }

    public List<RegistrationDTO> getRegistrationsByPortalId(JsonNode registrationsJson, String portalId) {
        List<RegistrationDTO> allRegistrations = parseRegistrations(registrationsJson);

        return allRegistrations.stream()
            .filter(r -> r.getPortalId().equals(portalId))
            .toList();
    }

    public boolean validateRegistrations(JsonNode registrationsJson) {
        if (registrationsJson == null || !registrationsJson.isArray()) {
            return false;
        }

        for (JsonNode node : registrationsJson) {
            if (!node.has("portalId") || !node.has("status")) {
                log.warn("Registration missing required fields: {}", node);
                return false;
            }
        }

        return true;
    }

    public JsonNode createEmptyRegistrations() {
        return objectMapper.createArrayNode();
    }
}
