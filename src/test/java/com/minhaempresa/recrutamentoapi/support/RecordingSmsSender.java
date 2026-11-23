package com.minhaempresa.recrutamentoapi.support;

import static org.awaitility.Awaitility.await;

import com.minhaempresa.recrutamentoapi.notification.sender.NotificationChannel;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationSender;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test SMS sender (registered before the log-only one): keeps every SMS so tests can read the codes. */
public class RecordingSmsSender implements NotificationSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final List<OutboundNotification> sent = new CopyOnWriteArrayList<>();

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(OutboundNotification notification) {
        sent.add(notification);
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    public List<OutboundNotification> sentTo(String e164Phone) {
        return sent.stream().filter(n -> n.recipient().equals(e164Phone)).toList();
    }

    /** Waits for the {@code count}-th SMS to the phone and returns its 6-digit code. */
    public String awaitCode(String e164Phone, int count) {
        await().atMost(Duration.ofSeconds(10)).until(() -> sentTo(e164Phone).size() >= count);
        Matcher matcher = CODE.matcher(sentTo(e164Phone).get(count - 1).textBody());
        if (!matcher.find()) {
            throw new AssertionError("No code in SMS");
        }
        return matcher.group(1);
    }

    public String awaitCode(String e164Phone) {
        return awaitCode(e164Phone, 1);
    }
}
