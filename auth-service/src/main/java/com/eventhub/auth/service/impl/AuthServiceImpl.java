package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakTokenClient;
import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.dto.KCLoginCommand;
import com.eventhub.auth.client.dto.KCLoginResponse;
import com.eventhub.auth.client.dto.KCRefreshCommand;
import com.eventhub.auth.client.dto.KCLogoutCommand;
import com.eventhub.auth.client.dto.KCCreateUserCommand;
import com.eventhub.auth.client.dto.KCUserRepresentation;
import com.eventhub.auth.client.dto.KCUserSessionsResponse;
import com.eventhub.auth.client.exception.KeycloakAuthenticationException;
import com.eventhub.auth.dto.request.*;
import com.eventhub.auth.dto.response.*;
import com.eventhub.auth.security.Actor;
import com.eventhub.auth.security.BusinessRole;
import com.eventhub.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Auth Service implementation.
 * Orchestrates Keycloak Token Client and User Client for auth operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final KeycloakTokenClient keycloakTokenClient;
    private final KeycloakUserClient keycloakUserClient;

    @Override
    public Mono<UserResponse> register(RegisterRequest request) {
        // Parse fullName into firstName and lastName
        String[] nameParts = request.getFullName().trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        KCCreateUserCommand command = KCCreateUserCommand.builder()
                .email(request.getEmail())
                .username(request.getEmail())
                .firstName(firstName)
                .lastName(lastName)
                .enabled(true)
                .emailVerified(false)
                .password(request.getPassword())
                .temporaryPassword(false)
                .realmRoles(List.of("CUSTOMER"))
                .build();

        log.info("Registering new user: {}", request.getEmail());

        return keycloakUserClient.createUser(command)
                .doOnSuccess(user -> log.info("User registered successfully: {}", user.getId()))
                .map(this::mapToUserResponse);
    }

    @Override
    public Mono<TokenResponse> login(LoginRequest request) {
        KCLoginCommand command = KCLoginCommand.builder()
                .code(request.getCode())
                .redirectUri(request.getRedirectUri())
                .build();

        log.debug("Exchanging auth code for tokens");

        return keycloakTokenClient.loginWithCode(command)
                .doOnSuccess(response -> log.debug("Login successful"))
                .map(this::mapToTokenResponse);
    }

    @Override
    public Mono<TokenResponse> refresh(RefreshRequest request) {
        KCRefreshCommand command = KCRefreshCommand.builder()
                .refreshToken(request.getRefreshToken())
                .build();

        log.debug("Refreshing access token");

        return keycloakTokenClient.refreshToken(command)
                .doOnSuccess(response -> log.debug("Token refresh successful"))
                .map(this::mapToTokenResponse);
    }

    @Override
    public Mono<Void> logout(RefreshRequest request) {
        KCLogoutCommand command = KCLogoutCommand.builder()
                .refreshToken(request.getRefreshToken())
                .build();

        log.debug("Logging out user (revoking refresh token)");

        return keycloakTokenClient.logout(command)
                .doOnSuccess(v -> log.debug("Logout successful"));
    }

    @Override
    public Mono<UserResponse> getMe(Actor actor) {
        log.debug("Getting profile for user: {}", actor.userId());

        return keycloakUserClient.getUser(actor.userId().toString())
                .map(this::mapToUserResponse);
    }

    @Override
    public Mono<Void> changePassword(Actor actor, ChangePasswordRequest request) {
        // Option: Use Keycloak Account API with user's access token
        // For now, use Admin API with service account to set new password
        // In production, consider using Account API with user's token for better UX
        log.info("Changing password for user: {}", actor.userId());

        return keycloakUserClient.resetPassword(
                actor.userId().toString(),
                "SET_TEMPORARY_PASSWORD",
                request.getNewPassword()
        );
    }

    @Override
    public Mono<Void> forgotPassword(ForgotPasswordRequest request) {
        log.info("Forgot password requested for email: {}", request.getEmail());

        // Find user by email, then trigger reset email
        // Return success even if user not found to prevent email enumeration
        return keycloakUserClient.getUsers(0, 1, request.getEmail())
                .flatMap(users -> {
                    if (users.isEmpty()) {
                        log.debug("No user found for email (preventing enumeration): {}", request.getEmail());
                        return Mono.empty();
                    }
                    return keycloakUserClient.sendForgotPasswordEmail(users.get(0).getId());
                });
    }

    @Override
    public Mono<Void> resetPassword(ResetPasswordRequest request) {
        // User provides reset token from email - Keycloak handles token validation
        // This would typically use Keycloak Account API with the reset token
        // For now, using Admin API as fallback (requires service account)
        // In production, implement Account API call with reset token
        log.info("Reset password with token");

        // Need to find user by reset token - Keycloak Account API handles this
        // Placeholder: would need userId from token validation
        return Mono.error(new KeycloakAuthenticationException("NOT_IMPLEMENTED", "Reset password with token requires Account API integration"));
    }

    @Override
    public Mono<Void> verifyEmail(VerifyEmailRequest request) {
        // Keycloak handles email verification via link in email
        // This endpoint can be used to verify token if needed
        log.info("Verify email with token");
        return Mono.empty();
    }

    @Override
    public Mono<Void> resendVerification(ResendVerificationRequest request) {
        log.info("Resend verification email for: {}", request.getEmail());

        return keycloakUserClient.getUsers(0, 1, request.getEmail())
                .flatMap(users -> {
                    if (users.isEmpty()) {
                        log.debug("No user found for email (preventing enumeration): {}", request.getEmail());
                        return Mono.empty();
                    }
                    return keycloakUserClient.sendVerifyEmail(users.get(0).getId());
                });
    }

    @Override
    public Mono<UserSessionsResponse> getMySessions(Actor actor) {
        log.debug("Getting sessions for user: {}", actor.userId());

        return keycloakUserClient.getUserSessions(actor.userId().toString())
                .map(this::mapToSessionsResponse);
    }

    @Override
    public Mono<Void> revokeMySession(Actor actor, String sessionId) {
        log.info("Revoking session {} for user: {}", sessionId, actor.userId());

        return keycloakUserClient.revokeSession(sessionId);
    }

    private UserResponse mapToUserResponse(KCUserRepresentation user) {
        return UserResponse.builder()
                .id(UUID.fromString(user.getId()))
                .email(user.getEmail())
                .fullName(buildFullName(user.getFirstName(), user.getLastName()))
                .role(extractBusinessRole(user.getRealmRoles()))
                .status(user.getEnabled() != null && user.getEnabled() ? UserResponse.UserStatus.ACTIVE : UserResponse.UserStatus.LOCKED)
                .createdAt(user.getCreatedTimestamp() != null ? Instant.ofEpochMilli(user.getCreatedTimestamp()) : null)
                .build();
    }

    private UserSessionsResponse mapToSessionsResponse(KCUserSessionsResponse kcResponse) {
        List<UserSessionsResponse.SessionInfo> sessions = kcResponse.getSessions().stream()
                .map(s -> UserSessionsResponse.SessionInfo.builder()
                        .id(s.getId())
                        .startedAt(s.getStart() != null ? Instant.ofEpochMilli(s.getStart()) : null)
                        .lastAccessedAt(s.getLastAccess() != null ? Instant.ofEpochMilli(s.getLastAccess()) : null)
                        .ipAddress(s.getIpAddress())
                        .userAgent(s.getUserAgent())
                        .device(s.getDevice())
                        .build())
                .toList();
        return UserSessionsResponse.builder().sessions(sessions).build();
    }

    private TokenResponse mapToTokenResponse(KCLoginResponse kcResponse) {
        return TokenResponse.builder()
                .accessToken(kcResponse.getAccessToken())
                .refreshToken(kcResponse.getRefreshToken())
                .tokenType(kcResponse.getTokenType())
                .expiresIn(kcResponse.getExpiresIn())
                .build();
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) return "";
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    private BusinessRole extractBusinessRole(List<String> realmRoles) {
        if (realmRoles == null || realmRoles.isEmpty()) {
            return BusinessRole.CUSTOMER;
        }
        return realmRoles.stream()
                .filter(r -> r.equals("CUSTOMER") || r.equals("ORGANIZER") || r.equals("ADMIN"))
                .map(BusinessRole::valueOf)
                .findFirst()
                .orElse(BusinessRole.CUSTOMER);
    }
}