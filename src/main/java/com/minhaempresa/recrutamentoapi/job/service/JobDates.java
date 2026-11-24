package com.minhaempresa.recrutamentoapi.job.service;

import com.minhaempresa.recrutamentoapi.shared.config.AppProperties;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "Today" for job rules (deadlines, public visibility), in {@code app.defaults.time-zone} (Angola).
 * Also part of the public cache keys ({@code @jobDates.today()}), so cached data changes at midnight.
 */
@Component("jobDates")
@RequiredArgsConstructor
public class JobDates {

    private final Clock clock;
    private final AppProperties appProperties;

    public LocalDate today() {
        return LocalDate.now(clock.withZone(appProperties.defaults().timeZone()));
    }
}
