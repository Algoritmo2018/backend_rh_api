package com.minhaempresa.recrutamentoapi.client.dto.response;

import com.minhaempresa.recrutamentoapi.client.entity.ClientSector;
import com.minhaempresa.recrutamentoapi.client.entity.ClientStatus;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full client record (frontend {@code ClientWithStats} without fees: the company does not charge fees).
 * Contains personal data of the contacts: administrators only.
 *
 * @param openJobs open jobs of the client (0 until the jobs module, stage 5)
 * @param totalJobs all jobs of the client
 * @param hires hired candidates (0 until the applications module, stage 8)
 */
public record ClientResponse(
        UUID id,
        String name,
        ClientSector sector,
        String nif,
        Province province,
        String city,
        String address,
        String website,
        List<ClientContactResponse> contacts,
        ClientStatus status,
        String notes,
        Instant createdAt,
        long openJobs,
        long totalJobs,
        long hires) {}
