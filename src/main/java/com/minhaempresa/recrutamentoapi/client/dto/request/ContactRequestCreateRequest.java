package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.client.entity.ContactRequestKind;
import com.minhaempresa.recrutamentoapi.client.validation.ValidContactRequest;
import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import com.minhaempresa.recrutamentoapi.shared.validation.PersonName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Public forms "Precisa de recrutar?" ({@code company}) and Contacto ({@code general}), with the rules
 * of the frontend {@code ContactForm}. {@code company} and {@code vacancies} are required only for
 * company requests.
 *
 * @param website honeypot: hidden in the form, so only robots fill it (the request is then discarded
 *     without any sign)
 */
@ValidContactRequest
public record ContactRequestCreateRequest(
        @NotNull(message = "{validation.required}") ContactRequestKind kind,
        @NotBlank(message = "{validation.required}") @PersonName String name,
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.email.invalid}") @Size(max = 160, message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.required}") @AngolanPhone String phone,
        @NotBlank(message = "{contact.message.min}")
        @Size(min = 10, message = "{contact.message.min}")
        @Size(max = 2000, message = "{contact.message.max}")
        String message,
        @Size(max = 120, message = "{contact.company.max}") String company,
        Integer vacancies,
        String website) {

    @Override
    public String toString() {
        // Personal data stays out of logs
        return "ContactRequestCreateRequest(kind=" + kind + ")";
    }
}
