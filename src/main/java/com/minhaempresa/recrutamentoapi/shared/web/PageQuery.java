package com.minhaempresa.recrutamentoapi.shared.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pagination query parameters: {@code ?page=1&pageSize=20}. Pages start at 1, like the frontend.
 * Bind with {@code @Valid PageQuery pageQuery} (no annotation needed on a controller parameter).
 *
 * @param page page number, 1 or more (default 1)
 * @param pageSize items per page, between 1 and {@value #MAX_PAGE_SIZE} (default {@value #DEFAULT_PAGE_SIZE})
 */
public record PageQuery(
        @Min(value = 1, message = "{validation.page.min}") Integer page,
        @Min(value = 1, message = "{validation.page-size.range}")
        @Max(value = MAX_PAGE_SIZE, message = "{validation.page-size.range}") Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public int pageOrDefault() {
        return page == null ? 1 : page;
    }

    public int pageSizeOrDefault() {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }

    /** Spring Data pageable (0-based) with the given sort. */
    public Pageable toPageable(Sort sort) {
        return PageRequest.of(pageOrDefault() - 1, pageSizeOrDefault(), sort);
    }

    public Pageable toPageable() {
        return toPageable(Sort.unsorted());
    }
}
