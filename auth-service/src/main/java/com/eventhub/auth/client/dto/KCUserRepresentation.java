package com.eventhub.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * User representation from Keycloak Admin API.
 * Maps key fields needed by Auth Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KCUserRepresentation {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Boolean emailVerified;
    private Long createdTimestamp;
    private List<String> realmRoles;
    private Map<String, List<String>> clientRoles;
    private Map<String, List<String>> attributes;
}