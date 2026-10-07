package com.back.car_rent.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Money received for a contract. A contract's payment status (Pending, Partial, Paid) is computed from its payments,
 * never typed by hand. {@code contractRef} is the contract's database id; the contract number and the client name
 * are copied for display when the payment is recorded.
 */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_contract", columnList = "contract_ref"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String paymentId;   // e.g. PY-1001
    @Column(name = "contract_ref")
    private Long contractRef;
    private String contractId;  // copied: CT-1001
    private String clientName;  // copied
    private Double amount;
    private String method;      // Cash, Card, Bank transfer
    private String date;        // ISO date, like the other records
    private String notes;
}
