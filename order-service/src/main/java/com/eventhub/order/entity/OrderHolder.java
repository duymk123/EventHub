package com.eventhub.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_holders")
@Getter
@Setter
@NoArgsConstructor
public class OrderHolder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;

    @Column(name = "holder_email", length = 255)
    private String holderEmail;

    @Column(name = "holder_phone", length = 20)
    private String holderPhone;
}
