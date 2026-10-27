package com.grievance360.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Builds a bounded Pageable from untrusted query parameters using a whitelist of sortable properties. */
public final class Paging {
    public static final int MAX_SIZE = 100;

    private Paging() {}

    public static Pageable of(Integer page, Integer size, String sort, Set<String> allowed, String defaultSort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);
        String spec = sort == null || sort.isBlank() ? defaultSort : sort;
        String[] parts = spec.split(",");
        String prop = parts[0].trim();
        if (!allowed.contains(prop)) {
            throw ApiException.badRequest("INVALID_SORT", "Sorting by '" + prop + "' is not supported");
        }
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(p, s, Sort.by(dir, prop).and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
