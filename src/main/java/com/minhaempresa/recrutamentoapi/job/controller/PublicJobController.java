package com.minhaempresa.recrutamentoapi.job.controller;

import com.minhaempresa.recrutamentoapi.job.dto.request.PublicJobQuery;
import com.minhaempresa.recrutamentoapi.job.dto.response.PublicJobResponse;
import com.minhaempresa.recrutamentoapi.job.service.PublicJobService;
import com.minhaempresa.recrutamentoapi.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Jobs of the public portal (no login): only open jobs whose deadline has not passed. */
@RestController
@RequestMapping("/api/v1/public/jobs")
@RequiredArgsConstructor
public class PublicJobController {

    /** The home page data may be up to a minute old in browsers and proxies. */
    private static final CacheControl HOME_PAGE_CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final PublicJobService publicJobService;

    /** Search page ({@code /vagas}): 9 per page by default, at most 50. */
    @GetMapping
    public PageResponse<PublicJobResponse> search(@Valid PublicJobQuery query) {
        return publicJobService.search(query);
    }

    @GetMapping("/featured")
    public ResponseEntity<List<PublicJobResponse>> featured(
            @RequestParam(defaultValue = "6")
            @Min(value = 1, message = "{job.featured-limit.range}") @Max(value = 12, message = "{job.featured-limit.range}") int limit) {
        return ResponseEntity.ok().cacheControl(HOME_PAGE_CACHE).body(publicJobService.featured(limit));
    }

    /** {@code {"oil_gas": 3, "hse": 1}}: areas without visible jobs are absent. */
    @GetMapping("/area-counts")
    public ResponseEntity<Map<String, Long>> areaCounts() {
        return ResponseEntity.ok().cacheControl(HOME_PAGE_CACHE).body(publicJobService.areaCounts());
    }

    @GetMapping("/{slug}")
    public PublicJobResponse getBySlug(@PathVariable String slug) {
        return publicJobService.getBySlug(slug);
    }
}
