package com.minhaempresa.recrutamentoapi.application.controller;

import com.minhaempresa.recrutamentoapi.application.dto.request.MyApplicationRequest;
import com.minhaempresa.recrutamentoapi.application.dto.response.ApplicationCreatedResponse;
import com.minhaempresa.recrutamentoapi.application.service.ApplicationService;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Applications of the logged-in candidate (the candidate is always the one of the access token). */
@RestController
@RequestMapping("/api/v1/me/applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CANDIDATE')")
public class MyApplicationController {

    private final ApplicationService applicationService;

    /**
     * Multipart: part {@code application} (JSON) and the optional file part {@code cv} (a CV for this
     * application; without it the profile's current CV is used). 201; duplicate → 409; job paused, closed or
     * past its deadline → 422; email not confirmed yet → 409.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationCreatedResponse apply(@Valid @RequestPart("application") MyApplicationRequest request,
            @RequestPart(value = "cv", required = false) MultipartFile cv, AuthenticatedUser actor) throws IOException {
        return applicationService.applyMine(actor, request, PublicApplicationController.upload(cv));
    }
}
