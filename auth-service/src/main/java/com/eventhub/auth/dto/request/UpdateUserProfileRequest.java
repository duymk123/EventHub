package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// DTO for updating user profile information - PATCH
@Data 
public class UpdateUserProfileRequest {
    @Size (max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    @Pattern (regexp = "^$|^[0-9+()\\- ]{8,20}$", message = "Phone number must contain only digits and an optional leading +")
    @Size (max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    @Size (max = 255, message = "Avatar URL must not exceed 255 characters")
    private String avatarUrl;
}
