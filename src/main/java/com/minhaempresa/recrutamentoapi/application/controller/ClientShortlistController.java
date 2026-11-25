package com.minhaempresa.recrutamentoapi.application.controller;

import com.minhaempresa.recrutamentoapi.application.dto.response.ClientSharedApplicationResponse;
import com.minhaempresa.recrutamentoapi.application.service.ClientShortlistService;
import com.minhaempresa.recrutamentoapi.candidate.dto.response.DocumentFile;
import com.minhaempresa.recrutamentoapi.shared.security.AuthenticatedUser;
import com.minhaempresa.recrutamentoapi.shared.web.FileResponses;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shortlist of the client portal (stage 8c, read only). The company always comes from the access token; another
 * company's job or application → 404 like a missing id (covered by {@code ClientPortalIsolationTest}).
 */
@RestController
@RequestMapping("/api/v1/client-portal")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class ClientShortlistController {

    private final ClientShortlistService shortlistService;

    @GetMapping("/jobs/{jobId}/shortlist")
    public List<ClientSharedApplicationResponse> shortlist(@PathVariable UUID jobId, AuthenticatedUser actor) {
        return shortlistService.shortlist(jobId, actor);
    }

    @GetMapping("/applications/{id}")
    public ClientSharedApplicationResponse application(@PathVariable UUID id, AuthenticatedUser actor) {
        return shortlistService.get(id, actor);
    }

    /** The "CV para o cliente" (PDF; {@code ?inline=true} to view it). No such file → 404. */
    @GetMapping("/applications/{id}/client-cv")
    public ResponseEntity<Resource> clientCv(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean inline, AuthenticatedUser actor) {
        DocumentFile file = shortlistService.clientCv(id, inline, actor);
        return FileResponses.of(file.content(), file.contentType(), file.fileName(), file.sizeBytes(), file.inline());
    }
}
