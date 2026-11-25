package com.minhaempresa.recrutamentoapi.messaging.listener;

import com.minhaempresa.recrutamentoapi.messaging.entity.MessageChannel;
import com.minhaempresa.recrutamentoapi.messaging.event.MessageDispatchEvent;
import com.minhaempresa.recrutamentoapi.messaging.service.MessageService;
import com.minhaempresa.recrutamentoapi.notification.event.OutboundEmailStatusEvent;
import com.minhaempresa.recrutamentoapi.notification.sender.NotificationDispatcher;
import com.minhaempresa.recrutamentoapi.notification.sender.OutboundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Delivery of messages to candidates and their status. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDeliveryListener {

    private final NotificationDispatcher dispatcher;
    private final MessageService messageService;

    /** SMS and WhatsApp, after the message is committed (simulated senders for now). */
    @Async
    @TransactionalEventListener
    public void onDispatch(MessageDispatchEvent event) {
        OutboundNotification notification = event.channel() == MessageChannel.WHATSAPP
                ? OutboundNotification.whatsapp(event.phone(), event.text())
                : OutboundNotification.sms(event.phone(), event.text());
        try {
            dispatcher.send(notification);
            messageService.applyDeliveryResult(event.messageId(), true, null);
        } catch (RuntimeException e) {
            log.warn("Message {} not delivered: {}", event.messageId(), e.getClass().getSimpleName());
            messageService.applyDeliveryResult(event.messageId(), false, e.getClass().getSimpleName());
        }
    }

    /** Emails: the outbox worker reports, in its transaction, when one was sent or definitely failed. */
    @EventListener
    public void onEmailStatus(OutboundEmailStatusEvent event) {
        messageService.applyDeliveryResult(event.referenceId(), event.sent(), event.error());
    }
}
