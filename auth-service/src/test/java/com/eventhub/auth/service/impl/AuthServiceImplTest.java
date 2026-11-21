package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakTokenClient;
import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.KeycloakUserClient.KeycloakUser;
import com.eventhub.auth.client.KeycloakUserClient.RoleRepresentation;
import com.eventhub.auth.common.BusinessRole;
import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.request.ChangePasswordRequest;
import com.eventhub.auth.dto.request.RegisterRequest;
import com.eventhub.auth.entity.UserProfile;
import com.eventhub.auth.exception.BusinessException;
import com.eventhub.auth.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {
    private static final RoleRepresentation CUSTOMER = new RoleRepresentation("r-c", "CUSTOMER");
    private static final RoleRepresentation ORGANIZER = new RoleRepresentation("r-o", "ORGANIZER");
    private static final RoleRepresentation DEFAULT = new RoleRepresentation("r-d", "default-roles-eventhub");

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private KeycloakTokenClient tokens;
    private KeycloakUserClient users;
    private UserProfileRepository profiles;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        tokens = mock(KeycloakTokenClient.class);
        users = mock(KeycloakUserClient.class);
        profiles = mock(UserProfileRepository.class);
        service = new AuthServiceImpl(tokens, users, profiles);
        when(users.getUser(userId)).thenReturn(new KeycloakUser(userId.toString(), "a@x.vn", "A", "B", true, false, 0L));
    }

    @Test
    void registerAssignsCustomerAndSendsVerificationEmail() {
        when(users.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn(userId);
        when(users.directRealmRoles(userId)).thenReturn(List.of(DEFAULT));
        when(users.availableRealmRoles(userId)).thenReturn(List.of(CUSTOMER, ORGANIZER));
        when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));

        var response = service.register(new RegisterRequest("A@X.vn", "Secret@123", "Nguyễn Văn A", null));

        verify(users).addRealmRoles(userId, List.of(CUSTOMER));
        verify(users).sendVerifyEmail(userId);
        assertThat(response.role()).isEqualTo("CUSTOMER");
        assertThat(response.emailVerified()).isFalse();
    }

    @Test
    void registerRollsBackKeycloakUserWhenRoleAssignmentFails() {
        when(users.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn(userId);
        when(users.directRealmRoles(userId)).thenReturn(List.of());
        when(users.availableRealmRoles(userId)).thenReturn(List.of(CUSTOMER));
        doThrow(new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE, "down"))
                .when(users).addRealmRoles(any(), any());

        assertThatThrownBy(() -> service.register(new RegisterRequest("a@x.vn", "Secret@123", "A", null)))
                .isInstanceOf(BusinessException.class);
        verify(users).deleteUserQuietly(userId);
        verify(profiles, never()).save(any());
    }

    @Test
    void verificationEmailFailureDoesNotFailRegistration() {
        when(users.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn(userId);
        when(users.directRealmRoles(userId)).thenReturn(List.of(CUSTOMER));
        when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));
        doThrow(new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE, "smtp")).when(users).sendVerifyEmail(userId);

        assertThat(service.register(new RegisterRequest("a@x.vn", "Secret@123", "A", null)).id()).isEqualTo(userId);
        verify(users, never()).deleteUserQuietly(any());
    }

    @Test
    void assignRoleReplacesPreviousBusinessRole() {
        when(users.directRealmRoles(userId)).thenReturn(List.of(DEFAULT, CUSTOMER));
        when(users.availableRealmRoles(userId)).thenReturn(List.of(ORGANIZER));
        when(users.effectiveRealmRoles(userId)).thenReturn(List.of("default-roles-eventhub", "ORGANIZER"));

        service.assignRole(adminId, userId, BusinessRole.ORGANIZER);

        verify(users).addRealmRoles(userId, List.of(ORGANIZER));
        verify(users).removeRealmRoles(userId, List.of(CUSTOMER));
    }

    @Test
    void assignRoleReportsConflictFromCompositeDefaultRole() {
        // Realm cũ: default-roles-eventhub vẫn chứa CUSTOMER nên gỡ mapping trực tiếp chưa đủ.
        when(users.directRealmRoles(userId)).thenReturn(List.of(DEFAULT));
        when(users.availableRealmRoles(userId)).thenReturn(List.of(ORGANIZER));
        when(users.effectiveRealmRoles(userId)).thenReturn(List.of("default-roles-eventhub", "CUSTOMER", "ORGANIZER"));

        assertCode(() -> service.assignRole(adminId, userId, BusinessRole.ORGANIZER), ErrorCode.ROLE_MAPPING_CONFLICT);
    }

    @Test
    void adminCannotModifyOwnAccount() {
        assertCode(() -> service.lockUser(adminId, adminId), ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        assertCode(() -> service.assignRole(adminId, adminId, BusinessRole.CUSTOMER), ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
    }

    @Test
    void lockDisablesAndRevokesSessions() {
        service.lockUser(adminId, userId);
        verify(users).setEnabled(userId, false);
        verify(users).logoutAllSessions(userId);
    }

    @Test
    void changePasswordChecksCurrentPassword() {
        when(tokens.verifyPassword("a@x.vn", "wrong")).thenReturn(false);
        assertCode(() -> service.changePassword(userId, new ChangePasswordRequest("wrong", "NewSecret@1")),
                ErrorCode.CURRENT_PASSWORD_INCORRECT);
        verify(users, never()).resetPassword(any(), anyString());

        when(tokens.verifyPassword("a@x.vn", "right")).thenReturn(true);
        service.changePassword(userId, new ChangePasswordRequest("right", "NewSecret@1"));
        verify(users).resetPassword(eq(userId), eq("NewSecret@1"));
    }

    @Test
    void getMeReportsEmailVerification() {
        when(users.effectiveRealmRoles(userId)).thenReturn(List.of("CUSTOMER"));
        when(profiles.findByKeycloakSub(userId.toString())).thenReturn(java.util.Optional.of(
                UserProfile.builder().fullName("Nguyễn Văn A").build()));
        var me = service.getMe(userId);
        assertThat(me.role()).isEqualTo("CUSTOMER");
        assertThat(me.emailVerified()).isFalse();
        assertThat(me.fullName()).isEqualTo("Nguyễn Văn A");
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
    }
}
