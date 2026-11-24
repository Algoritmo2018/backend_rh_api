package com.minhaempresa.recrutamentoapi.search.controller;

import com.minhaempresa.recrutamentoapi.search.dto.response.GlobalSearchResponse;
import com.minhaempresa.recrutamentoapi.search.service.GlobalSearchService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Global search of the internal panel (top bar). */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    /** At least 2 characters (shorter → empty result). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'INTERVIEWER')")
    public GlobalSearchResponse search(@RequestParam(defaultValue = "") @Size(max = 100, message = "{talent.search.size}") String q, AuthenticatedUser viewer) {
        return globalSearchService.search(q, viewer);
    }
}
