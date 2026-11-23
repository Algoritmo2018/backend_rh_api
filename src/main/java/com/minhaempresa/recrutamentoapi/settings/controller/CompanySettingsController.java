package com.minhaempresa.recrutamentoapi.settings.controller;

import com.minhaempresa.recrutamentoapi.settings.dto.request.CompanySettingsUpdateRequest;
import com.minhaempresa.recrutamentoapi.settings.dto.response.CompanySettingsResponse;
import com.minhaempresa.recrutamentoapi.settings.service.CompanySettingsService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Company settings (Configurações → Dados da empresa / Proteção de dados). Administrators only. */
@RestController
@RequestMapping("/api/v1/settings/company")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CompanySettingsController {

    private final CompanySettingsService service;

    @GetMapping
    public CompanySettingsResponse get() {
        return service.get();
    }

    @PatchMapping
    public CompanySettingsResponse update(@Valid @RequestBody CompanySettingsUpdateRequest request) {
        return service.update(request);
    }

    /** Multipart field {@code file}: PNG, JPEG or WebP up to 300 KB. */
    @PostMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanySettingsResponse uploadLogo(@RequestPart("file") MultipartFile file) throws IOException {
        return service.uploadLogo(file.getOriginalFilename(), file.getBytes());
    }

    @DeleteMapping("/logo")
    public CompanySettingsResponse deleteLogo() {
        return service.deleteLogo();
    }
}
