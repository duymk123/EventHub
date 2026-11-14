package com.eventhub.auth.dto.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@Builder 
@AllArgsConstructor 
@NoArgsConstructor 
public class UserProfileResponse {
    private UUID id;
    private String keycloakSub;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
}
