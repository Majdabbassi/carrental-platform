package com.back.car_rent.repository;

import com.back.car_rent.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> {

    /**
     * Reserved or active contracts of one car that overlap the nights [start, end). A car returned on the day
     * the next rental starts is free (dates are ISO strings, so comparing them as text is comparing days).
     */
    @Query("select c from Contract c where c.licensePlate = :plate and c.status in ('Reserved', 'Active') "
            + "and c.startDate < :end and c.endDate > :start and c.id <> :excludeId")
    List<Contract> findBlocking(@Param("plate") String plate, @Param("start") String start,
                                @Param("end") String end, @Param("excludeId") long excludeId);

    /** Reserved or active contracts of any car overlapping the nights [start, end). */
    @Query("select c from Contract c where c.status in ('Reserved', 'Active') "
            + "and c.startDate < :end and c.endDate > :start and c.id <> :excludeId")
    List<Contract> findAllBlocking(@Param("start") String start, @Param("end") String end,
                                   @Param("excludeId") long excludeId);

    /** Row lock on one contract (SELECT ... FOR UPDATE) while a payment is recorded against it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.id = :id")
    Optional<Contract> lockById(@Param("id") Long id);
}
