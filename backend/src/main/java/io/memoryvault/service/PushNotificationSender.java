package io.memoryvault.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.util.Map;

/**
 * Sends one Web Push notification via VAPID. {@code java.security.Security} needs the
 * BouncyCastle provider registered once at startup for the EC crypto this library uses —
 * done in the constructor rather than relying on it being present already.
 */
@Component
public class PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationSender.class);

    private final PushService pushService;
    // The browser's real PushSubscription.toJSON() includes fields (e.g. expirationTime)
    // that nl.martijndwars.webpush.Subscription doesn't declare — found by testing against
    // a genuine subscription rather than a hand-written fixture. Ignore unknown properties
    // instead of failing every real-world subscription.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final boolean configured;

    public PushNotificationSender(
            @Value("${app.vapid.public-key:}") String publicKey,
            @Value("${app.vapid.private-key:}") String privateKey,
            @Value("${app.vapid.subject:mailto:support@memoryvault.dev}") String subject
    ) {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        this.configured = !publicKey.isBlank() && !privateKey.isBlank();

        PushService service = null;
        if (configured) {
            try {
                service = new PushService(publicKey, privateKey, subject);
            } catch (Exception ex) {
                log.warn("Failed to initialize PushService with configured VAPID keys: {}", ex.getMessage());
            }
        }
        this.pushService = service;
    }

    /**
     * @param subscriptionJson the raw PushSubscription JSON the browser produced
     * @param title            notification title
     * @param body             notification body
     * @param vaultItemId      included in the payload so the click handler can open the right item
     * @return true if the push was accepted by the push service; false on any failure
     *         (expired subscription, misconfiguration, network error) — never throws,
     *         since one failed notification must never break the batch send
     */
    public boolean send(String subscriptionJson, String title, String body, Long vaultItemId) {
        if (!configured || pushService == null) {
            log.debug("Push not sent: VAPID keys not configured");
            return false;
        }
        try {
            Subscription subscription = objectMapper.readValue(subscriptionJson, Subscription.class);
            String payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body, "vaultItemId", vaultItemId));

            Notification notification = new Notification(subscription, payload);
            // The no-encoding send() overload defaults to the legacy AESGCM scheme, which
            // uses a deprecated Crypto-Key header FCM now rejects outright ("crypto-key
            // header had invalid format") -- found by testing against real FCM, not from
            // documentation. AES128GCM (RFC 8291) is the modern, currently-required scheme.
            var response = pushService.send(notification, Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            String responseBody = response.getEntity() != null
                    ? new String(response.getEntity().getContent().readAllBytes())
                    : "(no body)";
            log.warn("Push send returned status {}: {}", status, responseBody);
            return false;
        } catch (Exception ex) {
            log.warn("Push send failed: {}", ex.getMessage());
            return false;
        }
    }
}
