package com.eventhub.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Command for creating a user in Keycloak via Admin API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KCCreateUserCommand {
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Boolean emailVerified;
    private String password;
    private Boolean temporaryPassword;
    private List<String> realmRoles;
}