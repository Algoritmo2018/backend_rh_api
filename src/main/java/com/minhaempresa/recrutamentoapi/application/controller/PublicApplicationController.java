package com.minhaempresa.recrutamentoapi.application.controller;

import com.minhaempresa.recrutamentoapi.application.dto.request.PublicApplicationRequest;
import com.minhaempresa.recrutamentoapi.application.dto.response.ApplicationReceivedResponse;
import com.minhaempresa.recrutamentoapi.application.service.ApplicationService;
import com.minhaempresa.recrutamentoapi.application.service.ApplicationService.CvUpload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Public application form (no account). */
@RestController
@RequestMapping("/api/v1/public/applications")
@RequiredArgsConstructor
public class PublicApplicationController {

    private final ApplicationService applicationService;

    /**
     * Multipart: part {@code application} (JSON) and the optional file part {@code cv} (PDF, DOCX, PNG or JPEG,
     * 5 MB). Always 202 {@code {status: "received"}} for a visible job, also when the email already applied to it
     * (that email is told instead); a job that is not visible → 404.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApplicationReceivedResponse apply(@Valid @RequestPart("application") PublicApplicationRequest request,
            @RequestPart(value = "cv", required = false) MultipartFile cv, HttpServletRequest http) throws IOException {
        return applicationService.applyPublic(request, upload(cv), http.getRemoteAddr());
    }

    static CvUpload upload(MultipartFile cv) throws IOException {
        return cv == null || cv.isEmpty() ? CvUpload.NONE : new CvUpload(cv.getOriginalFilename(), cv.getBytes());
    }
}
