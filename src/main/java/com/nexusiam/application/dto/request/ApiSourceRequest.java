package com.nexusiam.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSourceRequest {

    @NotBlank(message = "API path cannot be blank")
    @Pattern(regexp = "^/.*", message = "API path must start with /")
    private String apiPath;

    @NotBlank(message = "API method cannot be blank")
    @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE)$", message = "Invalid HTTP method")
    private String apiMethod;

    private String module;

    private String description;
}
