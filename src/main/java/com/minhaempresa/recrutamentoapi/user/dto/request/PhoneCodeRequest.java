package com.minhaempresa.recrutamentoapi.user.dto.request;

import com.minhaempresa.recrutamentoapi.shared.validation.AngolanPhone;
import jakarta.validation.constraints.NotBlank;

/** Send a confirmation code to the new phone number (needed when two-step verification is on). */
public record PhoneCodeRequest(@NotBlank(message = "{validation.required}") @AngolanPhone String phone) {}
