package com.minhaempresa.recrutamentoapi.shared.exception;

import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.web.PageQuery;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that trigger every kind of error handled by the API. */
@RestController
@RequestMapping("/api/v1/test-support")
class ErrorTestController {

    record SampleRequest(
            @NotBlank(message = "{validation.required}") String name,
            @NotNull(message = "{validation.required}") Province province) {}

    record SampleResponse(String name, Province province, Instant at) {}

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException("error.not-found");
    }

    @GetMapping("/business")
    void business() {
        throw new BusinessException("error.business", List.of(new ApiException.FieldError("email", "validation.required")));
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("error.conflict");
    }

    @GetMapping("/forbidden")
    void forbidden() {
        throw new ForbiddenException();
    }

    @GetMapping("/unauthorized")
    void unauthorized() {
        throw new UnauthorizedException();
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("internal detail that must not leak");
    }

    @PostMapping("/samples")
    SampleResponse create(@Valid @RequestBody SampleRequest request) {
        return new SampleResponse(request.name(), request.province(), Instant.parse("2026-11-03T09:30:00Z"));
    }

    @GetMapping("/page")
    PageResponse<Integer> page(@Valid PageQuery pageQuery) {
        List<Integer> all = IntStream.rangeClosed(1, 23).boxed().toList();
        var pageable = pageQuery.toPageable();
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return PageResponse.from(new PageImpl<>(all.subList(from, to), pageable, all.size()), n -> n);
    }

    @GetMapping("/province")
    Province province(@RequestParam Province value) {
        return value;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin-only")
    String adminOnly() {
        return "ok";
    }
}
