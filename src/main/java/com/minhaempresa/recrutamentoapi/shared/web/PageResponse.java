package com.minhaempresa.recrutamentoapi.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Paginated response, identical to the frontend type {@code Paginated<T>}.
 *
 * @param items items of the current page
 * @param total total number of items across all pages
 * @param page current page, starting at 1
 * @param pageSize maximum items per page
 * @param totalPages number of pages (at least 1, like the frontend)
 */
public record PageResponse<T>(List<T> items, long total, int page, int pageSize, int totalPages) {

    /** Converts a Spring Data page (0-based) and maps each element to its DTO. */
    public static <E, T> PageResponse<T> from(Page<E> source, Function<? super E, ? extends T> mapper) {
        List<T> items = source.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(
                items,
                source.getTotalElements(),
                source.getNumber() + 1,
                source.getSize(),
                Math.max(1, source.getTotalPages()));
    }
}
