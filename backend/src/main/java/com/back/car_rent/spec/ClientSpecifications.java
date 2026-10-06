package com.back.car_rent.spec;

import com.back.car_rent.model.Client;
import org.springframework.data.jpa.domain.Specification;

public class ClientSpecifications {

    public static Specification<Client> fullNameContains(String q) {
        return (root, query, cb) -> q == null || q.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("fullName")), "%" + q.toLowerCase() + "%");
    }

    public static Specification<Client> emailContains(String q) {
        return (root, query, cb) -> q == null || q.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("email")), "%" + q.toLowerCase() + "%");
    }

    public static Specification<Client> phoneContains(String q) {
        return (root, query, cb) -> q == null || q.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("phone")), "%" + q.toLowerCase() + "%");
    }

    public static Specification<Client> statusEquals(String status) {
        return (root, query, cb) -> status == null || status.isBlank()
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("status")), status.toLowerCase());
    }
}