package com.eventhub.analytics.websocket;

import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.config.ActorAuthenticationConverter;
import com.eventhub.analytics.service.EventAccessService;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Chặn và kiểm tra các frame STOMP client gửi vào. CONNECT xác thực JWT, SUBSCRIBE kiểm tra destination và
 * owner, registry cung cấp principal còn hạn cho các thao tác tiếp theo.
 */
@Component
public class DashboardAuthorizationInterceptor implements ChannelInterceptor {
    private static final Pattern DESTINATION = Pattern.compile(
            "^/topic/event/([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})/dashboard$");
    private final JwtDecoder decoder;
    private final ActorAuthenticationConverter converter;
    private final DashboardSessionRegistry sessions;
    private final EventAccessService access;

    public DashboardAuthorizationInterceptor(JwtDecoder decoder, ActorAuthenticationConverter converter,
            DashboardSessionRegistry sessions, EventAccessService access) {
        this.decoder = decoder;
        this.converter = converter;
        this.sessions = sessions;
        this.access = access;
    }

    /**
     * Xử lý frame trước broker. CONNECT giải mã JWT và gắn Authentication, SUBSCRIBE chấp nhận exact
     * destination có UUID rồi kiểm tra owner, các command client SEND bị từ chối.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getSessionId() == null) {
            throw new AccessDeniedException("Invalid dashboard frame");
        }
        StompCommand command = headers.getCommand();
        // Bước xác thực cấp STOMP diễn ra sau khi HTTP handshake đã mở socket.
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            String authorization = headers.getFirstNativeHeader("Authorization");
            // Gỡ header nhạy cảm khỏi frame trước khi chuyển vào các bước xử lý tiếp theo.
            headers.removeNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new AccessDeniedException("Dashboard token required");
            }
            try {
                var jwt = decoder.decode(authorization.substring(7));
                var authentication = converter.convert(jwt);
                Actor actor = (Actor) authentication.getPrincipal();
                if (actor.role() == com.eventhub.analytics.common.BusinessRole.CUSTOMER) {
                    throw new AccessDeniedException("Dashboard access denied");
                }
                sessions.authenticate(headers.getSessionId(), authentication, jwt.getExpiresAt());
                // Spring dùng principal này cho các frame và subscription của session.
                headers.setUser(authentication);
                return message;
            } catch (RuntimeException error) {
                throw new AccessDeniedException("Invalid dashboard identity");
            }
        }
        Authentication user = (Authentication) sessions.requireAuthenticated(headers.getSessionId());
        // Mỗi destination cần có event UUID đúng mẫu và quyền owner/ADMIN được xác minh qua Event.
        if (command == StompCommand.SUBSCRIBE) {
            String destination = headers.getDestination();
            var match = DESTINATION.matcher(destination == null ? "" : destination);
            if (!match.matches()) {
                throw new AccessDeniedException("Dashboard destination denied");
            }
            access.assertCanView((Actor) user.getPrincipal(), UUID.fromString(match.group(1)));
        } else if (command != null && command != StompCommand.UNSUBSCRIBE && command != StompCommand.DISCONNECT) {
            throw new AccessDeniedException("Dashboard command denied");
        }
        return message;
    }
}
