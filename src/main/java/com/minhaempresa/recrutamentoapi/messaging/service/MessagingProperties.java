package com.minhaempresa.recrutamentoapi.messaging.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Messages to candidates ({@code app.messages.*}), decision D7-7.
 *
 * @param perSenderHour messages a user may send per hour
 * @param perSenderDay messages a user may send per 24 hours
 * @param perCandidateDay messages a candidate may receive per 24 hours (manual sends; automatic ones count)
 */
@ConfigurationProperties(prefix = "app.messages")
public record MessagingProperties(Integer perSenderHour, Integer perSenderDay, Integer perCandidateDay) {

    public MessagingProperties {
        perSenderHour = perSenderHour == null ? 100 : perSenderHour;
        perSenderDay = perSenderDay == null ? 500 : perSenderDay;
        perCandidateDay = perCandidateDay == null ? 10 : perCandidateDay;
    }
}
