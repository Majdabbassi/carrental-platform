package com.back.car_rent.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByContractRefOrderByDateAscIdAsc(Long contractRef);

    boolean existsByContractRef(Long contractRef);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.contractRef = :contractRef")
    double totalPaid(@Param("contractRef") Long contractRef);
}
