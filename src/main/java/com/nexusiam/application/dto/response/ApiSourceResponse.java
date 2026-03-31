package com.nexusiam.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSourceResponse {

    private Long id;

    private String apiPath;

    private String apiMethod;

    private String module;

    private String description;

    private Instant createdDate;

    private Instant updatedDate;
}
