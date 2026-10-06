package com.back.car_rent.repository;

import com.back.car_rent.model.Car;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CarRepository extends JpaRepository<Car, Long>, JpaSpecificationExecutor<Car> {

    Optional<Car> findFirstByLicensePlate(String licensePlate);

    /**
     * Row lock on the car (SELECT ... FOR UPDATE): bookings of the same car queue up behind each other, so the
     * "is it free?" check and the insert of the contract cannot interleave.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Car c where c.licensePlate = :plate")
    List<Car> lockByLicensePlate(@Param("plate") String plate);
}
