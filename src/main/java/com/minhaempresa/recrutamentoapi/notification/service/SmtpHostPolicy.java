package com.minhaempresa.recrutamentoapi.notification.service;

import com.minhaempresa.recrutamentoapi.notification.sender.MailSenderProperties;
import com.minhaempresa.recrutamentoapi.shared.exception.ApiException.FieldError;
import com.minhaempresa.recrutamentoapi.shared.exception.BusinessException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SMTP hosts typed by administrators must be public servers ({@code app.mail.block-private-hosts}, on in
 * production): a host resolving to loopback, link-local (169.254.x.x, cloud metadata), private,
 * shared (100.64.0.0/10) or unspecified addresses would let the server be used to probe the internal
 * network. Checked when saving, when testing and again before connecting (the name may resolve
 * differently later). The error never says what the host resolved to.
 */
@Component
@RequiredArgsConstructor
public class SmtpHostPolicy {

    private final MailSenderProperties properties;

    /** @throws BusinessException 422 on {@code host} */
    public void requireAllowed(String host) {
        if (!properties.blockPrivateHosts()) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BusinessException("settings.email-account.host-unknown", List.of(new FieldError("host", "settings.email-account.host-unknown")));
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new BusinessException("settings.email-account.host-not-allowed", List.of(new FieldError("host", "settings.email-account.host-not-allowed")));
            }
        }
    }

    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 0.0.0.0/8 "this network" and 100.64.0.0/10 shared address space (carrier NAT)
            return first == 0 || (first == 100 && (second & 0xC0) == 64);
        }
        if (address instanceof Inet6Address) {
            // fc00::/7 unique local addresses
            return (bytes[0] & 0xFE) == 0xFC;
        }
        return false;
    }
}
