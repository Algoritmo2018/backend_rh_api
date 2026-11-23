package com.minhaempresa.recrutamentoapi.settings.controller;

import com.minhaempresa.recrutamentoapi.settings.dto.response.PublicSettingsResponse;
import com.minhaempresa.recrutamentoapi.settings.service.CompanySettingsService;
import com.minhaempresa.recrutamentoapi.settings.service.CompanySettingsService.LogoFile;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public company data: contacts, consent texts and logo (no login). */
@RestController
@RequestMapping("/api/v1/public/settings")
@RequiredArgsConstructor
public class PublicSettingsController {

    private final CompanySettingsService service;

    @GetMapping
    public PublicSettingsResponse get() {
        return service.getPublic();
    }

    /**
     * The logo. With the current version ({@code ?v=<hash>}, as in {@code logoUrl}) it is cached for a
     * year ({@code immutable}); otherwise the client must revalidate with the {@code ETag}. 404 when
     * there is no logo.
     */
    @GetMapping("/logo")
    public ResponseEntity<Resource> logo(@RequestParam(name = "v", required = false) String version,
                                         @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        LogoFile logo = service.loadLogo();
        String etag = "\"" + logo.hash() + "\"";
        CacheControl cacheControl = logo.hash().equals(version)
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()
                : CacheControl.noCache();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cacheControl).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(cacheControl)
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .body(logo.content());
    }
}
