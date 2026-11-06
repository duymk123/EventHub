package com.eventhub.analytics.config;

import com.eventhub.analytics.messaging.InvalidEventException;
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
        // KafkaTemplate được dùng để publish record lỗi vào analytics.dlq.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition("analytics.dlq", record.partition()));
        // Chỉ hoàn tất nhánh recover khi Kafka đã xác nhận lưu record DLQ.
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
        // InvalidEventException được tách khỏi lỗi tạm thời để chọn cách xử lý phù hợp.
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }
}
