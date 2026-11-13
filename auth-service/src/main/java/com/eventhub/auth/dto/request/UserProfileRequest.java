package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
public class UserProfileRequest {

    @NotBlank(message = "Full name is required")
    @Size (max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    @Size (max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    @Size (max = 255, message = "Avatar URL must not exceed 255 characters")
    private String avatarUrl;
}
