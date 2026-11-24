package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.client.entity.ClientSector;
import com.minhaempresa.recrutamentoapi.client.entity.ClientStatus;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * New client (frontend {@code ClientInput}, same rules as {@code ClientFormDialog}). A new client needs
 * its primary contact. The frontend field {@code feePercentage} is ignored: the company does not charge
 * fees.
 *
 * @param nif Angolan tax identification number (9 to 14 digits)
 * @param status {@code active} when omitted
 */
public record ClientCreateRequest(
        @NotBlank(message = "{client.name.size}") @Size(min = 2, max = 120, message = "{client.name.size}") String name,
        @NotNull(message = "{validation.required}") ClientSector sector,
        @NotBlank(message = "{validation.nif.invalid}") @Pattern(regexp = "\\d{9,14}", message = "{validation.nif.invalid}") String nif,
        @NotNull(message = "{validation.required}") Province province,
        @NotBlank(message = "{client.city.size}") @Size(min = 2, max = 80, message = "{client.city.size}") String city,
        @NotBlank(message = "{client.address.size}") @Size(min = 3, max = 200, message = "{client.address.size}") String address,
        @Pattern(regexp = "|https?://\\S+\\.\\S+", message = "{validation.url.invalid}") @Size(max = 200, message = "{validation.url.invalid}") String website,
        ClientStatus status,
        @Size(max = 1000, message = "{client.notes.size}") String notes,
        @NotEmpty(message = "{client.contacts.required}") @Size(max = 20, message = "{client.contacts.max}")
        List<@Valid @NotNull(message = "{validation.required}") ClientContactRequest> contacts) {}
