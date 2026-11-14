package com.eventhub.analytics.config;

import com.eventhub.analytics.websocket.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;
import java.util.Arrays;

/**
 * Cấu hình endpoint /ws/dashboard, SimpleBroker và hai chiều interceptor. Native WebSocket dùng STOMP CONNECT
 * để mang JWT, Origin được lấy từ cấu hình frontend.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final DashboardAuthorizationInterceptor authorization;
    private final DashboardSessionRegistry sessions;
    private final String[] origins;

    public WebSocketConfig(DashboardAuthorizationInterceptor authorization,
            DashboardSessionRegistry sessions, Environment environment) {
        this.authorization = authorization;
        this.sessions = sessions;
        origins = Arrays.stream(environment.getProperty("eventhub.websocket.allowed-origins", "").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
        if (Arrays.asList(origins).contains("*")) {
            throw new IllegalArgumentException("Dashboard requires explicit origins");
        }
    }

    /**
     * Mở native WebSocket tại /ws/dashboard với các Origin cụ thể được cấu hình. Token được chuyển ở frame
     * STOMP CONNECT sau bước handshake.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/dashboard").setAllowedOrigins(origins);
    }

    /**
     * Bật SimpleBroker cho destination /topic/event/ và thứ tự publish trong từng client session. Cấu hình này
     * phục vụ một Analytics instance.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic/event/");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }

    /**
     * Gắn interceptor xác thực CONNECT và phân quyền SUBSCRIBE trước khi frame đi vào broker.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorization);
    }

    /**
     * Kiểm tra session còn xác thực ngay trước khi gửi MESSAGE ra client, hỗ trợ chặn dữ liệu ở thời điểm token
     * đã hết hạn.
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            /**
             * Đọc sessionId của message sắp gửi, chỉ chuyển tiếp dữ liệu dashboard khi registry xác nhận
             * session còn hạn.
             */
            @Override
            public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel) {
                var headers = org.springframework.messaging.simp.stomp.StompHeaderAccessor.wrap(message);
                if (headers.getMessageType() == org.springframework.messaging.simp.SimpMessageType.MESSAGE) {
                    try {
                        sessions.requireAuthenticated(headers.getSessionId());
                    } catch (org.springframework.security.access.AccessDeniedException denied) {
                        // Interceptor trả null để dừng MESSAGE của session đã hết hạn ngay trước khi gửi.
                        return null;
                    }
                }
                return message;
            }
        });
    }

    /**
     * Gắn registry theo dõi socket, giới hạn frame 64 KiB, buffer 512 KiB, thời gian gửi 10 giây và thời gian
     * chờ message đầu tiên 15 giây.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessions).setMessageSizeLimit(65536)
                .setSendBufferSizeLimit(524288).setSendTimeLimit(10000).setTimeToFirstMessage(15000);
    }
}
