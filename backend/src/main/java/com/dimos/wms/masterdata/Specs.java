package com.dimos.wms.masterdata;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/** Small helpers for list filters. */
final class Specs {

    private static final char ESC = '\\';

    private Specs() {
    }

    static <E> Specification<E> bySite(Long siteId) {
        return (root, q, cb) -> cb.equal(root.get("siteId"), siteId);
    }

    static <E> Specification<E> equal(String field, Object value) {
        return value == null ? null : (root, q, cb) -> cb.equal(root.get(field), value);
    }

    /** Case-insensitive "contains" on any of the given text fields. */
    static <E> Specification<E> search(String text, String... fields) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.trim().toLowerCase()
                .replace(String.valueOf(ESC), ESC + "" + ESC)
                .replace("%", ESC + "%")
                .replace("_", ESC + "_");
        String pattern = "%" + escaped + "%";
        return (root, q, cb) -> {
            Predicate[] ors = new Predicate[fields.length];
            for (int i = 0; i < fields.length; i++) {
                ors[i] = cb.like(cb.lower(root.get(fields[i])), pattern, ESC);
            }
            return cb.or(ors);
        };
    }

    @SafeVarargs
    static <E> Specification<E> all(Specification<E>... parts) {
        Specification<E> result = (root, q, cb) -> cb.conjunction();
        for (Specification<E> p : parts) {
            if (p != null) {
                result = result.and(p);
            }
        }
        return result;
    }
}
