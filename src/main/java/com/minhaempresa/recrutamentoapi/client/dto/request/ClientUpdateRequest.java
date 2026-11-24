package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.client.entity.ClientSector;
import com.minhaempresa.recrutamentoapi.client.entity.ClientStatus;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Partial update ({@code PATCH}, frontend {@code Partial<ClientInput>}): {@code null} fields are left
 * unchanged. {@code contacts}, when present, is the full list (see {@link ClientContactRequest}).
 * {@code status: inactive} deactivates the client: its portal users lose access at once.
 */
public record ClientUpdateRequest(
        @Size(min = 2, max = 120, message = "{client.name.size}") String name,
        ClientSector sector,
        @Pattern(regexp = "\\d{9,14}", message = "{validation.nif.invalid}") String nif,
        Province province,
        @Size(min = 2, max = 80, message = "{client.city.size}") String city,
        @Size(min = 3, max = 200, message = "{client.address.size}") String address,
        @Pattern(regexp = "|https?://\\S+\\.\\S+", message = "{validation.url.invalid}") @Size(max = 200, message = "{validation.url.invalid}") String website,
        ClientStatus status,
        @Size(max = 1000, message = "{client.notes.size}") String notes,
        @Size(max = 20, message = "{client.contacts.max}")
        List<@Valid @NotNull(message = "{validation.required}") ClientContactRequest> contacts) {}
