package com.eventhub.notification.config;

import com.eventhub.notification.messaging.InvalidEventException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Cấu hình cách consumer xử lý lỗi thông qua DefaultErrorHandler. DeadLetterPublishingRecoverer dùng
 * KafkaTemplate để phát record thất bại sang DLQ của service.
 */
@Configuration
public class KafkaConfig {
    /**
     * Spring Boot gắn bean này vào listener container. Lỗi tạm thời dùng lịch retry 1/2/4 giây,
     * InvalidEventException đi DLQ, recoverer chỉ hoàn tất khi Kafka xác nhận publish DLQ.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        // Ở đây service đóng vai trò publisher cho DLQ, template là KafkaTemplate do Spring Boot tạo.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition("notification.dlq", record.partition()));
        // Lỗi gửi DLQ phải nổi lên để container còn xử lý lại record nguồn.
        recoverer.setFailIfSendResultIsError(true);
        // Metadata DLQ chỉ giữ loại lỗi, hạn chế đưa payload và dữ liệu cá nhân vào thông tin exception.
        recoverer.setExceptionHeadersCreator((headers, exception, key, names) ->
                headers.add("eventhub-error", org.springframework.core.NestedExceptionUtils.getMostSpecificCause(exception).getClass().getSimpleName()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(4000);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Worker khác còn lease sẽ được chờ lâu hơn lịch retry lỗi SMTP/DB thông thường.
        handler.setBackOffFunction((record, exception) ->
                org.springframework.core.NestedExceptionUtils.getMostSpecificCause(exception)
                        instanceof com.eventhub.notification.messaging.DeliveryInProgressException
                        ? new org.springframework.util.backoff.FixedBackOff(5000, 30) : backOff);
        // Payload sai contract có thể được đối soát ở DLQ, dữ liệu gốc được giữ trong record lỗi.
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
