package com.eventhub.analytics.messaging;

import com.eventhub.analytics.messaging.event.BusinessEvent;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * Cổng kiểm tra dữ liệu đầu vào của các consumer. JSON được chuyển thành record Java đúng loại, sau đó kiểm tra
 * Bean Validation, eventType và Kafka key.
 */
@Component
public class EventDecoder {
    private final ObjectMapper mapper;
    private final Validator validator;

    public EventDecoder(ObjectMapper mapper, Validator validator) {
        // Dùng mapper riêng cho đầu vào Kafka để các thay đổi cấu hình parse nằm trong decoder này.
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

        // Từ chối UUID rút gọn hoặc dữ liệu sai kiểu ngay ở bước giải mã event.
        this.mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(java.util.UUID.class, new JsonDeserializer<java.util.UUID>() {
                    /**
                     * Bộ đọc UUID cho payload Kafka, yêu cầu chuỗi UUID chuẩn 36 ký tự trước khi chuyển sang
                     * java.util.UUID.
                     */
                    @Override
                    public java.util.UUID deserialize(com.fasterxml.jackson.core.JsonParser parser,
                            DeserializationContext context) throws java.io.IOException {
                        if (!parser.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_STRING)
                                || !parser.getText().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                            throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "Invalid UUID field");
                        }
                        return java.util.UUID.fromString(parser.getText());
                    }
                }));

        this.validator = validator;
    }

    /**
     * Nhận JSON value, loại record cần đọc, tên topic và key từ ConsumerRecord. Parse/validate toàn bộ event và
     * kiểm tra aggregateId trùng key, lỗi contract được chuyển thành InvalidEventException an toàn cho DLQ.
     */
    public <T extends BusinessEvent> T decode(String body, Class<T> type, String topic, String key) {
        try {
            // Parse đúng loại record theo topic rồi kiểm tra các annotation validation lồng nhau.
            T event = mapper.readValue(body, type);
            if (event == null || !validator.validate(event).isEmpty()
                    || !topic.equals(event.eventType())
                    || !event.aggregateId().toString().equals(key)) {
                throw new InvalidEventException("Invalid event contract");
            }
            return event;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) {
            throw new InvalidEventException("Invalid event JSON");
        }
    }
}
