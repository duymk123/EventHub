package com.eventhub.order.model;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.mapping.Column;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidColumnMappingTests {

    @Test
    void mysqlPrimaryKeysAndReferencesUseChar36() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", MySQLDialect.class.getName())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.type.preferred_uuid_jdbc_type", "CHAR")
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(Order.class)
                    .addAnnotatedClass(OrderItem.class)
                    .addAnnotatedClass(Payment.class)
                    .addAnnotatedClass(Refund.class)
                    .buildMetadata();

            for (Class<?> entity : new Class<?>[]{Order.class, OrderItem.class, Payment.class, Refund.class}) {
                assertThat(metadata.getEntityBinding(entity.getName())
                        .getIdentifier().getType().getReturnedClass()).isEqualTo(UUID.class);
                assertChar36(metadata, entity, "id");
            }
            assertChar36(metadata, Order.class, "user_id");
            assertChar36(metadata, OrderItem.class, "ticket_type_id");
            assertChar36(metadata, OrderItem.class, "order_id");
            assertChar36(metadata, Payment.class, "order_id");
            assertChar36(metadata, Refund.class, "order_id");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void assertChar36(Metadata metadata, Class<?> entity, String columnName) {
        Column column = metadata.getEntityBinding(entity.getName())
                .getTable().getColumn(new Column(columnName));
        assertThat(column).as("%s.%s", entity.getSimpleName(), columnName).isNotNull();
        assertThat(column.getSqlType(metadata))
                .as("MySQL type of %s.%s", entity.getSimpleName(), columnName)
                .isEqualToIgnoringCase("char(36)");
    }
}
