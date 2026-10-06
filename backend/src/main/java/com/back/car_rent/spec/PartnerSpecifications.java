package com.back.car_rent.spec;

import com.back.car_rent.model.Partner;
import org.springframework.data.jpa.domain.Specification;

public class PartnerSpecifications {

    public static Specification<Partner> partnerIdContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("partnerId")), like);
    }

    public static Specification<Partner> companyNameContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("companyName")), like);
    }

    public static Specification<Partner> typeEquals(String type) {
        if (type == null || type.isBlank()) return null;
        String s = type.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("type")), s);
    }

    public static Specification<Partner> statusEquals(String status) {
        if (status == null || status.isBlank()) return null;
        String s = status.toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("status")), s);
    }

    public static Specification<Partner> emailContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("email")), like);
    }

    public static Specification<Partner> phoneContains(String term) {
        if (term == null || term.isBlank()) return null;
        String like = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("phone")), like);
    }
}