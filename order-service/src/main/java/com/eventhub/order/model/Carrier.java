package com.eventhub.order.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Example entity that extends {@link BaseEntity} to inherit auditing fields.
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "carriers")
public class Carrier extends BaseEntity {

    // Add domain‑specific fields here, for example:
    // private String name;
    // private String code;
}
