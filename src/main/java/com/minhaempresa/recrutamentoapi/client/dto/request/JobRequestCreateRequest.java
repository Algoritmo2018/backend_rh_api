package com.minhaempresa.recrutamentoapi.client.dto.request;

import com.minhaempresa.recrutamentoapi.client.entity.JobRequestUrgency;
import com.minhaempresa.recrutamentoapi.shared.domain.JobArea;
import com.minhaempresa.recrutamentoapi.shared.domain.Money;
import com.minhaempresa.recrutamentoapi.shared.domain.Province;
import com.minhaempresa.recrutamentoapi.shared.validation.ValidMoney;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * New job request from the client portal (frontend {@code JobRequestInput}, rules of
 * {@code ClientJobRequestPage}). The client and the requesting user come from the access token: any
 * {@code clientId} / {@code requestedById} in the body is ignored.
 *
 * @param area only the key ({@code oil_gas}), never the Portuguese label
 * @param desiredStartDate {@code "YYYY-MM-DD"}, not in the past (Angola time)
 */
public record JobRequestCreateRequest(
        @NotBlank(message = "{job-request.title.size}") @Size(min = 3, max = 120, message = "{job-request.title.size}") String title,
        @NotNull(message = "{job-request.area.required}") JobArea area,
        @NotNull(message = "{validation.required}") Province province,
        @NotNull(message = "{validation.required}") @Min(value = 1, message = "{job-request.vacancies.min}") @Max(value = 200, message = "{job-request.vacancies.max}") Integer vacancies,
        @NotBlank(message = "{job-request.description.size}") @Size(min = 20, max = 3000, message = "{job-request.description.size}") String description,
        @NotBlank(message = "{job-request.requirements.size}") @Size(min = 10, max = 3000, message = "{job-request.requirements.size}") String requirements,
        @ValidMoney Money salaryBudget,
        LocalDate desiredStartDate,
        @NotNull(message = "{validation.required}") JobRequestUrgency urgency) {}
