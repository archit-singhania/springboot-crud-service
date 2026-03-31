package com.nexusiam.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InternalUserRequest {

    @NotBlank(message = "Role is required")
    @Pattern(
            regexp = "^(SUPER_ADMIN|ADMIN|VIEW_ONLY|AUDIT_COMPLIANCE)$",
            message = "Role must be one of the above mentioned"
    )
    private String role;

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(\\.[A-Za-z]{2,})?$",
            message = "Invalid or unsupported email domain"
    )
    private String email;

    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    @Pattern(
            regexp = "^(ABC|DEF|GHI|XYZ)$",
            message = "Organisation must be one of the mentioned above"
    )
    private String organisation;

    @Pattern(
            regexp = "^(PENDING|ACTIVE|DISABLED)$",
            message = "Status must be one of: PENDING, ACTIVE, DISABLED"
    )
    private String status;

    private String notes;

    private Long createdBy;
}
