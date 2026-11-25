package com.minhaempresa.recrutamentoapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.minhaempresa.recrutamentoapi.notification.entity.EmailPurpose;
import com.minhaempresa.recrutamentoapi.notification.sender.EmailNotificationSender;
import com.minhaempresa.recrutamentoapi.notification.sender.MailHeaders;
import com.minhaempresa.recrutamentoapi.notification.sender.MailSenderProperties;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
import com.minhaempresa.recrutamentoapi.notification.service.DirectEmailSender;
import com.minhaempresa.recrutamentoapi.notification.service.SmtpHostPolicy;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import jakarta.mail.internet.InternetAddress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.MailSendException;

/** Header injection, private SMTP hosts and the in-memory attempts of secret-link emails. */
class MailSafetyTest {

    static MailSenderProperties properties(boolean blockPrivateHosts, List<Duration> directRetryDelays) {
        return new MailSenderProperties("FEH OIRAM <nao-responder@fehoiram.test>", blockPrivateHosts,
                new MailSenderProperties.Outbox(Duration.ofSeconds(10), 20, List.of(Duration.ofMinutes(1)), Duration.ofDays(30), Duration.ofDays(90)),
                directRetryDelays);
    }

    @Nested
    class HeaderInjection {

        @Test
        void lineBreaksNeverStartANewHeader() {
            assertThat(MailHeaders.clean("Ana\r\nBcc: x@evil.com")).isEqualTo("Ana Bcc: x@evil.com");
            assertThat(MailHeaders.clean("Olá Mundo\tok\u0000")).isEqualTo("Olá Mundo ok");
            assertThat(MailHeaders.clean(null)).isNull();
        }

        @Test
        void theDisplayNameStaysOneLine() throws Exception {
            InternetAddress address = MailHeaders.address("ana@teste.ao", "Ana\r\nBcc: x@evil.com");

            assertThat(address.getAddress()).isEqualTo("ana@teste.ao");
            assertThat(address.getPersonal()).isEqualTo("Ana Bcc: x@evil.com");
            assertThat(address.toString()).doesNotContain("\r").doesNotContain("\n");
        }

        @ParameterizedTest
        @ValueSource(strings = {"ana@teste.ao, x@evil.com", "ana@teste.ao\r\nBcc: x@evil.com", "grupo: a@b.ao;", "sem-arroba", ""})
        void onlyOneMailboxIsAccepted(String value) {
            assertThatThrownBy(() -> MailHeaders.address(value, null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class PrivateHosts {

        final SmtpHostPolicy blocking = new SmtpHostPolicy(properties(true, null));

        @ParameterizedTest
        @ValueSource(strings = {"127.0.0.1", "localhost", "0.0.0.0", "10.0.0.5", "172.16.3.4", "192.168.1.10", "169.254.169.254", "100.64.0.1", "::1", "fd00::1", "fe80::1"})
        void internalAddressesAreRefusedOnTheHostField(String host) {
            assertThatThrownBy(() -> blocking.requireAllowed(host))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getMessageKey()).isEqualTo("settings.email-account.host-not-allowed");
                        assertThat(e.getFieldErrors()).singleElement().satisfies(error -> assertThat(error.field()).isEqualTo("host"));
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {"8.8.8.8", "41.63.0.10", "2001:4860:4860::8888"})
        void publicAddressesAreAccepted(String host) {
            blocking.requireAllowed(host);
        }

        @Test
        void unknownHostsAreRefusedWithoutDetails() {
            assertThatThrownBy(() -> blocking.requireAllowed("nao-existe.invalid"))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessageKey()).isEqualTo("settings.email-account.host-unknown"));
        }

        @Test
        void theCheckCanBeTurnedOffForDevelopment() {
            new SmtpHostPolicy(properties(false, null)).requireAllowed("127.0.0.1");
        }
    }

    @Nested
    class DirectAttempts {

        final OutboundNotification email = OutboundNotification.email(EmailPurpose.SYSTEM, "ana@teste.ao", "Convite", "texto", null);

        @Test
        void threeAttemptsByDefault() {
            assertThat(properties(false, null).directRetryDelays()).hasSize(2);
        }

        @Test
        void aTemporaryFailureIsRetried() {
            EmailNotificationSender sender = mock(EmailNotificationSender.class);
            doThrow(new MailSendException("down")).doThrow(new MailSendException("down")).doNothing().when(sender).send(email);

            boolean sent = new DirectEmailSender(sender, properties(false, List.of(Duration.ofMillis(1), Duration.ofMillis(1)))).send(email);

            assertThat(sent).isTrue();
            verify(sender, times(3)).send(email);
        }

        @Test
        void afterThreeFailuresTheEmailIsGivenUp() {
            EmailNotificationSender sender = mock(EmailNotificationSender.class);
            doThrow(new MailSendException("down")).when(sender).send(email);

            boolean sent = new DirectEmailSender(sender, properties(false, List.of(Duration.ofMillis(1), Duration.ofMillis(1)))).send(email);

            assertThat(sent).isFalse();
            verify(sender, times(3)).send(email);
        }

        @Test
        void aFirstSuccessSendsOnce() {
            EmailNotificationSender sender = mock(EmailNotificationSender.class);
            doNothing().when(sender).send(email);

            new DirectEmailSender(sender, properties(false, List.of(Duration.ofMillis(1)))).send(email);

            verify(sender, times(1)).send(email);
        }
    }
}
