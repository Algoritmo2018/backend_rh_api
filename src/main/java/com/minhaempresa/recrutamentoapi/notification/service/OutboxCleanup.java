package com.minhaempresa.recrutamentoapi.notification.service;

import com.minhaempresa.recrutamentoapi.notification.repository.OutboundEmailRepository;
import com.minhaempresa.recrutamentoapi.notification.sender.MailSenderProperties;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily retention of the outbox (decision D7-10): delivered emails are deleted after 30 days (their
 * bodies have personal data), failed ones after 90.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxCleanup {

    private final OutboundEmailRepository repository;
    private final MailSenderProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${app.mail.outbox.cleanup-cron:0 20 3 * * *}", zone = "${app.defaults.time-zone}")
    @Transactional
    public int deleteExpired() {
        Instant now = clock.instant();
        int deleted = repository.deleteExpired(now.minus(properties.outbox().sentRetention()), now.minus(properties.outbox().failedRetention()));
        if (deleted > 0) {
            log.info("Deleted {} old outbox emails", deleted);
        }
        return deleted;
    }
}
