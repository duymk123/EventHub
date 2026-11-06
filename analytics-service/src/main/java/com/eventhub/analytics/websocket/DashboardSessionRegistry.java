package com.eventhub.analytics.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Theo dõi socket và thời hạn xác thực trong bộ nhớ Analytics instance. Cho CONNECT 15 giây, sau xác thực dùng
 * exp của JWT và định kỳ đóng socket hết hạn.
 */
@Component
public class DashboardSessionRegistry implements WebSocketHandlerDecoratorFactory {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public DashboardSessionRegistry(Clock clock) {
        this.clock = clock;
    }

    /**
     * Bọc WebSocketHandler để theo dõi thời điểm mở/đóng socket. Sau khi mở, session có 15 giây hoàn tất xác
     * thực CONNECT.
     */
    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            /**
             * Ghi socket vào registry với hạn chờ CONNECT, xóa lại nếu handler bên trong mở kết nối thất bại.
             */
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), new Session(session, clock.instant().plusSeconds(15), null));
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception error) {
                    sessions.remove(session.getId());
                    throw error;
                }
            }

            /**
             * Xóa session khỏi registry rồi thông báo handler bên trong về việc đóng kết nối.
             */
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /**
     * Gắn principal và hạn exp vào socket đang mở, chỉ chấp nhận khi phiên chờ CONNECT còn hạn và chưa xác thực
     * trước đó.
     */
    public void authenticate(String id, Principal user, Instant expiry) {
        sessions.compute(id, (key, current) -> {
            if (current == null || current.user() != null || !clock.instant().isBefore(current.expiry())
                    || !clock.instant().isBefore(expiry) || !current.socket().isOpen()) {
                throw new org.springframework.security.access.AccessDeniedException("Invalid dashboard connection");
            }
            return new Session(current.socket(), expiry, user);
        });
    }

    /**
     * Lấy principal của session đang còn hạn, AccessDeniedException chặn thao tác khi session thiếu xác thực
     * hoặc đã hết thời gian.
     */
    public Principal requireAuthenticated(String id) {
        Session session = sessions.get(id);
        if (session == null || session.user() == null || !clock.instant().isBefore(session.expiry())) {
            throw new org.springframework.security.access.AccessDeniedException("Dashboard authentication required");
        }
        return session.user();
    }

    /**
     * Mỗi giây kiểm tra hạn chờ CONNECT/hạn token, đóng socket vi phạm và xóa registry trong finally.
     */
    @Scheduled(fixedDelay = 1000)
    public void closeExpired() {
        for (var entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (!clock.instant().isBefore(session.expiry())) {
                try {
                    session.socket().close(CloseStatus.POLICY_VIOLATION);
                } catch (Exception ignored) {
                    // Socket có thể đã đóng ở tầng transport, finally vẫn dọn session khỏi registry.
                } finally {
                    sessions.remove(entry.getKey(), session);
                }
            }
        }
    }

    /**
     * Thông tin quản lý một WebSocket trong bộ nhớ.
     *
     * @param socket WebSocket đang mở tương ứng với session.
     * @param expiry Hạn chờ CONNECT hoặc thời điểm hết hạn JWT của session.
     * @param user Principal đã được xác thực, null khi đang chờ CONNECT.
     */
    private record Session(WebSocketSession socket, Instant expiry, Principal user) {}
}
