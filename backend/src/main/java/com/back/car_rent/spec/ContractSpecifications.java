package com.back.car_rent.spec;

import com.back.car_rent.model.Contract;
import org.springframework.data.jpa.domain.Specification;

public class ContractSpecifications {

    public static Specification<Contract> contractIdContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("contractId")), like);
    }

    public static Specification<Contract> clientNameContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("clientName")), like);
    }

    public static Specification<Contract> phoneContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("clientPhone")), like);
    }

    public static Specification<Contract> carMakeContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("carMake")), like);
    }

    public static Specification<Contract> carModelContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("carModel")), like);
    }

    public static Specification<Contract> statusEquals(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("status")), s);
    }

    public static Specification<Contract> paymentStatusEquals(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("paymentStatus")), s);
    }

    public static Specification<Contract> rentalTypeEquals(String rentalType) {
        if (rentalType == null || rentalType.isBlank()) return null;
        String s = rentalType.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("rentalType")), s);
    }
}