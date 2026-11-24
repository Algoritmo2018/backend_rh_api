package com.minhaempresa.recrutamentoapi.notification.dto.request;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailSecurity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * SMTP account (create, replace or test). Gmail / Microsoft 365: {@code smtp.gmail.com} /
 * {@code smtp.office365.com}, port 587, {@code starttls}, with an app password.
 *
 * @param password write-only; blank keeps the stored one (on update and on a test of a saved account)
 * @param replyTo optional {@code Reply-To} address
 * @param active {@code null} = active
 */
public record EmailAccountRequest(
        @NotBlank(message = "{settings.email-account.name.size}") @Size(min = 2, max = 60, message = "{settings.email-account.name.size}") String name,
        @NotBlank(message = "{settings.email-account.host.invalid}") @Size(max = 253, message = "{settings.email-account.host.invalid}")
        @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$", message = "{settings.email-account.host.invalid}") String host,
        @NotNull(message = "{validation.required}") @Min(value = 1, message = "{settings.email-account.port.range}") @Max(value = 65535, message = "{settings.email-account.port.range}") Integer port,
        @NotNull(message = "{validation.required}") EmailSecurity security,
        @Size(max = 254, message = "{settings.email-account.username.size}") String username,
        @Size(max = 512, message = "{settings.email-account.password.size}") String password,
        @NotBlank(message = "{validation.email.invalid}") @Email(message = "{validation.email.invalid}") @Size(max = 254, message = "{validation.email.invalid}") String fromAddress,
        @Size(max = 80, message = "{settings.email-account.from-name.size}") String fromName,
        @Email(message = "{validation.email.invalid}") @Size(max = 254, message = "{validation.email.invalid}") String replyTo,
        Boolean active) {

    @Override
    public String toString() {
        return "EmailAccountRequest(name=" + name + ", host=" + host + ")";
    }
}
