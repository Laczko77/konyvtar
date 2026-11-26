package com.library.repository;

import com.library.entity.Book;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BookSpecification {

    private static final char LIKE_ESCAPE_CHAR = '\\';

    private BookSpecification() {
    }

    public static Specification<Book> withFilters(String title, String author, Boolean available) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(title)) {
                predicates.add(containsIgnoreCase(criteriaBuilder, root.get("title"), title));
            }

            if (StringUtils.hasText(author)) {
                predicates.add(containsIgnoreCase(criteriaBuilder, root.get("author"), author));
            }

            if (available != null) {
                predicates.add(criteriaBuilder.equal(root.get("available"), available));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate containsIgnoreCase(
            CriteriaBuilder criteriaBuilder,
            Expression<String> field,
            String rawValue
    ) {
        String escaped = escapeLikePattern(rawValue.trim().toLowerCase(Locale.ROOT));
        return criteriaBuilder.like(criteriaBuilder.lower(field), "%" + escaped + "%", LIKE_ESCAPE_CHAR);
    }

    private static String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
