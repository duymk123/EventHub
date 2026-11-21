package com.eventhub.auth.client.impl;

import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.dto.KCCreateUserCommand;
import com.eventhub.auth.client.dto.KCUserRepresentation;
import com.eventhub.auth.client.dto.KCUserSessionsResponse;
import com.eventhub.auth.client.exception.KeycloakAuthenticationException;
import com.eventhub.auth.client.exception.KeycloakUnavailableException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of KeycloakUserClient using Keycloak Admin Client.
 * Wraps blocking Admin Client calls in Mono for reactive usage.
 * Business roles managed: CUSTOMER, ORGANIZER, ADMIN.
 * Compatible with Keycloak 25.0.6 Admin Client API.
 */
// Slf4j là một annotation của Lombok, nó tự động tạo ra một logger cho class. 
// Khi bạn sử dụng @Slf4j, bạn có thể sử dụng log để ghi log trong class mà không cần phải khai báo logger thủ công.
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakUserClientImpl implements KeycloakUserClient {

    private final Keycloak keycloak;
    private static final String REALM = "eventhub";
    private static final List<String> BUSINESS_ROLES = List.of("CUSTOMER", "ORGANIZER", "ADMIN");

    private UsersResource usersResource() {
        return keycloak.realm(REALM).users();
    }

    private UserResource userResource(String userId) {
        return usersResource().get(userId);
    }

    @Override
    public Mono<KCUserRepresentation> createUser(KCCreateUserCommand command) {
        return Mono.fromCallable(() -> {
            UserRepresentation user = new UserRepresentation();
            user.setEmail(command.getEmail());
            user.setUsername(command.getUsername() != null ? command.getUsername() : command.getEmail());
            user.setFirstName(command.getFirstName());
            user.setLastName(command.getLastName());
            user.setEnabled(command.getEnabled() != null ? command.getEnabled() : true);
            user.setEmailVerified(command.getEmailVerified() != null ? command.getEmailVerified() : false);

            // Create user via Admin API
            Response response = usersResource().create(user);
            if (response.getStatus() >= 400) {
                String error = response.readEntity(String.class);
                throw new KeycloakAuthenticationException("CREATE_USER_FAILED", error);
            }

            // Extract user ID from Location header
            String location = response.getLocation().toString();
            String userId = location.substring(location.lastIndexOf('/') + 1);
            response.close();

            // Set password if provided
            if (command.getPassword() != null && !command.getPassword().isEmpty()) {
                CredentialRepresentation cred = new CredentialRepresentation();
                cred.setType(CredentialRepresentation.PASSWORD);
                cred.setValue(command.getPassword());
                cred.setTemporary(command.getTemporaryPassword() != null ? command.getTemporaryPassword() : false);
                userResource(userId).resetPassword(cred);
            }

            // Assign realm roles if provided
            if (command.getRealmRoles() != null && !command.getRealmRoles().isEmpty()) {
                List<RoleRepresentation> roles = command.getRealmRoles().stream()
                        .map(roleName -> keycloak.realm(REALM).roles().get(roleName).toRepresentation())
                        .collect(Collectors.toList());
                userResource(userId).roles().realmLevel().add(roles);
            }

            // Return created user
            return mapToDTO(userResource(userId).toRepresentation());
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak create user failed", ex);
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("409") || msg.contains("Conflict"))) {
                return new KeycloakAuthenticationException("EMAIL_ALREADY_EXISTS", "Email already exists");
            }
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<KCUserRepresentation> getUser(String userId) {
        return Mono.fromCallable(() -> {
            UserRepresentation user = userResource(userId).toRepresentation();
            if (user == null) {
                throw new KeycloakAuthenticationException("USER_NOT_FOUND", "User not found");
            }
            return mapToDTO(user);
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak get user failed: userId={}", userId, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<List<KCUserRepresentation>> getUsers(int first, int max, String search) {
        return Mono.fromCallable(() -> {
            List<UserRepresentation> users;
            if (search != null && !search.isBlank()) {
                users = usersResource().search(search, first, max);
            } else {
                users = usersResource().list(first, max);
            }
            return users.stream().map(this::mapToDTO).collect(Collectors.toList());
        }).onErrorMap(ex -> {
            log.error("Keycloak get users failed", ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<Void> setEnabled(String userId, boolean enabled) {
        return Mono.fromCallable(() -> {
            UserRepresentation user = userResource(userId).toRepresentation();
            if (user == null) {
                throw new KeycloakAuthenticationException("USER_NOT_FOUND", "User not found");
            }
            user.setEnabled(enabled);
            userResource(userId).update(user);
            return (Void) null;
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak set enabled failed: userId={}, enabled={}", userId, enabled, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<Void> assignRole(String userId, String role) {
        return Mono.fromCallable(() -> {
            // Validate role is a business role
            if (!BUSINESS_ROLES.contains(role)) {
                throw new KeycloakAuthenticationException("INVALID_ROLE", "Invalid business role: " + role);
            }

            UserResource userRes = userResource(userId);
            UserRepresentation user = userRes.toRepresentation();
            if (user == null) {
                throw new KeycloakAuthenticationException("USER_NOT_FOUND", "User not found");
            }

            // Get current business roles and remove them
            List<RoleRepresentation> currentRoles = userRes.roles().realmLevel().listAll();
            List<RoleRepresentation> businessRolesToRemove = currentRoles.stream()
                    .filter(r -> BUSINESS_ROLES.contains(r.getName()))
                    .collect(Collectors.toList());

            if (!businessRolesToRemove.isEmpty()) {
                userRes.roles().realmLevel().remove(businessRolesToRemove);
            }

            // Add new business role
            RoleRepresentation newRole = keycloak.realm(REALM).roles().get(role).toRepresentation();
            userRes.roles().realmLevel().add(Collections.singletonList(newRole));
            return (Void) null;
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak assign role failed: userId={}, role={}", userId, role, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<Void> resetPassword(String userId, String action, String temporaryPassword) {
        return Mono.fromCallable(() -> {
            UserResource userRes = userResource(userId);
            UserRepresentation user = userRes.toRepresentation();
            if (user == null) {
                throw new KeycloakAuthenticationException("USER_NOT_FOUND", "User not found");
            }

            if ("SEND_EMAIL".equals(action)) {
                // Send reset password email via execute actions
                userRes.executeActionsEmail(List.of("UPDATE_PASSWORD"));
            } else if ("SET_TEMPORARY_PASSWORD".equals(action)) {
                // Admin sets temporary password
                CredentialRepresentation cred = new CredentialRepresentation();
                cred.setType(CredentialRepresentation.PASSWORD);
                cred.setValue(temporaryPassword != null ? temporaryPassword : generateTempPassword());
                cred.setTemporary(true);
                userRes.resetPassword(cred);
            } else {
                throw new KeycloakAuthenticationException("INVALID_ACTION", "Invalid reset password action: " + action);
            }
            return (Void) null;
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak reset password failed: userId={}, action={}", userId, action, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<KCUserSessionsResponse> getUserSessions(String userId) {
        return Mono.fromCallable(() -> {
            UserResource userRes = userResource(userId);
            UserRepresentation user = userRes.toRepresentation();
            if (user == null) {
                throw new KeycloakAuthenticationException("USER_NOT_FOUND", "User not found");
            }

            // Get user sessions from user resource
            List<UserSessionRepresentation> sessions = userRes.getUserSessions();
            return mapToSessionsDTO(sessions);
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak get user sessions failed: userId={}", userId, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<Void> revokeAllSessions(String userId) {
        return Mono.fromCallable(() -> {
            UserResource userRes = userResource(userId);
            UserRepresentation user = userRes.toRepresentation();
            if (user == null) {
                throw new KeycloakAuthenticationException("USER_NOT_FOUND", "User not found");
            }
            userRes.logout();
            return (Void) null;
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak revoke all sessions failed: userId={}", userId, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        return Mono.fromCallable(() -> {
            // Delete session via realm sessions resource (requires force=false parameter)
            keycloak.realm(REALM).deleteSession(sessionId, false);
            return (Void) null;
        }).onErrorMap(ex -> {
            log.error("Keycloak revoke session failed: sessionId={}", sessionId, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<Void> sendVerifyEmail(String userId) {
        return Mono.fromCallable(() -> {
            UserResource userRes = userResource(userId);
            UserRepresentation user = userRes.toRepresentation();
            if (user == null) {
                throw new KeycloakAuthenticationException("USER_NOT_FOUND", "User not found");
            }
            userRes.executeActionsEmail(List.of("VERIFY_EMAIL"));
            return (Void) null;
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak send verify email failed: userId={}", userId, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    @Override
    public Mono<Void> sendForgotPasswordEmail(String userId) {
        return Mono.fromCallable(() -> {
            UserResource userRes = userResource(userId);
            UserRepresentation user = userRes.toRepresentation();
            if (user == null) {
                throw new KeycloakAuthenticationException("USER_NOT_FOUND", "User not found");
            }
            userRes.executeActionsEmail(List.of("UPDATE_PASSWORD"));
            return (Void) null;
        }).onErrorMap(ex -> {
            if (ex instanceof KeycloakAuthenticationException) return ex;
            log.error("Keycloak send forgot password email failed: userId={}", userId, ex);
            return new KeycloakUnavailableException("Keycloak Admin API unavailable", ex);
        });
    }

    private KCUserRepresentation mapToDTO(UserRepresentation user) {
        return KCUserRepresentation.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .createdTimestamp(user.getCreatedTimestamp())
                .realmRoles(user.getRealmRoles() != null ? user.getRealmRoles().stream()
                        .filter(BUSINESS_ROLES::contains)
                        .collect(Collectors.toList()) : List.of())
                .clientRoles(user.getClientRoles())
                .attributes(user.getAttributes())
                .build();
    }

    private KCUserSessionsResponse mapToSessionsDTO(List<UserSessionRepresentation> sessions) {
        List<KCUserSessionsResponse.KCSessionRepresentation> sessionDTOs = sessions.stream()
                .map(s -> KCUserSessionsResponse.KCSessionRepresentation.builder()
                        .id(s.getId())
                        .start(s.getStart())
                        .lastAccess(s.getLastAccess())
                        .ipAddress(s.getIpAddress())
                        .userAgent(getUserAgent(s))
                        .build())
                .collect(Collectors.toList());
        return KCUserSessionsResponse.builder().sessions(sessionDTOs).build();
    }

    private String getUserAgent(UserSessionRepresentation session) {
        // UserSessionRepresentation may not have getUserAgent() in older versions
        try {
            return (String) session.getClass().getMethod("getUserAgent").invoke(session);
        } catch (Exception e) {
            return null;
        }
    }

    private String generateTempPassword() {
        return "TempPass" + System.currentTimeMillis();
    }
}