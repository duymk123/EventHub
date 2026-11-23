package com.eventhub.auth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra request Admin API gửi tới Keycloak. Dùng HttpServer của JDK thay vì chạy Keycloak thật.
 */
class KeycloakUserClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/protocol/openid-connect/token")) {
                byte[] token = "{\"access_token\":\"admin-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, token.length);
                exchange.getResponseBody().write(token);
            } else {
                requests.add(exchange.getRequestMethod() + " " + path + " " + body);
                exchange.sendResponseHeaders(204, -1);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private KeycloakUserClient client() {
        KeycloakTokenClient tokens = new KeycloakTokenClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), "eventhub", "cid", "secret");
        return new KeycloakUserClient("http://127.0.0.1:" + server.getAddress().getPort(), "eventhub", tokens);
    }

    /**
     * Chỉ được có MỘT lệnh gọi: send-verify-email. Nếu code gắn trước requiredActions VERIFY_EMAIL thì user bị đặt
     * vào trạng thái chưa hoàn tất thiết lập và không đăng nhập được, trái với quyết định "chưa xác minh vẫn đăng
     * nhập được".
     */
    @Test
    void sendVerifyEmailOnlyAsksKeycloakToSendTheMail() {
        UUID userId = UUID.randomUUID();

        client().sendVerifyEmail(userId);

        String users = "/admin/realms/eventhub/users/" + userId;
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0)).startsWith("PUT " + users + "/send-verify-email");
    }

    /**
     * forgot-password là endpoint công khai: nếu gắn requiredActions UPDATE_PASSWORD trước khi gửi mail thì bất kỳ
     * ai biết email cũng khoá được tài khoản người khác (Keycloak trả "Account is not fully set up").
     */
    @Test
    void sendUpdatePasswordEmailDoesNotLockTheAccount() throws Exception {
        UUID userId = UUID.randomUUID();

        client().sendUpdatePasswordEmail(userId);

        String users = "/admin/realms/eventhub/users/" + userId;
        // Đúng MỘT lệnh gọi: execute-actions-email. Không có lệnh PUT lên chính user để gắn requiredActions.
        assertThat(requests).hasSize(1);
        String request = requests.get(0);
        assertThat(request).startsWith("PUT " + users + "/execute-actions-email");
        // Action nằm trong thân lệnh gọi, Keycloak chỉ áp vào lúc user bấm link.
        assertThat(JSON.readTree(request.substring(request.indexOf('['))))
                .extracting(JsonNode::asText).containsExactly("UPDATE_PASSWORD");
    }
}
