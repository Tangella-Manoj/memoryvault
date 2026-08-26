package io.memoryvault.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.subethamail.smtp.helper.SimpleMessageListener;
import org.subethamail.smtp.server.SMTPServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Self-hosted inbound-email fallback (Upgrade 3) for when no Mailgun credentials are
 * configured — this project's environment has none, so this is the path actually
 * exercised end to end. Listens on {@code app.smtp-receiver.port} (default 2525; real
 * port 25 needs root and is out of scope for local/dev use) and hands every accepted
 * message to {@link EmailIngestionService}.
 */
@Component
public class SmtpReceiverConfig implements SimpleMessageListener {

    private static final Logger log = LoggerFactory.getLogger(SmtpReceiverConfig.class);

    private final EmailIngestionService emailIngestionService;
    private final boolean enabled;
    private final int port;
    private SMTPServer smtpServer;

    public SmtpReceiverConfig(
            EmailIngestionService emailIngestionService,
            @Value("${app.smtp-receiver.enabled:true}") boolean enabled,
            @Value("${app.smtp-receiver.port:2525}") int port
    ) {
        this.emailIngestionService = emailIngestionService;
        this.enabled = enabled;
        this.port = port;
    }

    @PostConstruct
    void start() {
        if (!enabled) {
            log.info("SMTP receiver disabled (app.smtp-receiver.enabled=false)");
            return;
        }
        smtpServer = SMTPServer.port(port)
                .simpleMessageListener(this)
                .build();
        smtpServer.start();
        log.info("SMTP receiver listening on port {}", port);
    }

    @PreDestroy
    void stop() {
        if (smtpServer != null) {
            smtpServer.stop();
        }
    }

    @Override
    public boolean accept(String from, String recipient) {
        // Accept everything at the SMTP envelope level; unknown-recipient handling
        // happens in EmailIngestionService so it's identical to the Mailgun webhook path.
        return true;
    }

    @Override
    public void deliver(String from, String recipient, InputStream data) throws IOException {
        try {
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage message = new MimeMessage(session, data);

            String subject = message.getSubject() != null ? message.getSubject() : "(no subject)";
            String[] bodies = extractBodies(message);

            emailIngestionService.ingest(recipient, subject, bodies[0], bodies[1]);
        } catch (Exception ex) {
            log.warn("Failed to process inbound email to {}: {}", recipient, ex.getMessage());
        }
    }

    /**
     * @return a 2-element array: {@code [plainTextBody, htmlBody]}, either possibly null
     */
    private String[] extractBodies(MimeMessage message) throws Exception {
        Object content = message.getContent();

        if (content instanceof String text) {
            boolean isHtml = message.isMimeType("text/html");
            return isHtml ? new String[]{null, text} : new String[]{text, null};
        }

        if (content instanceof Multipart multipart) {
            String plain = null;
            String html = null;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/plain") && plain == null) {
                    plain = (String) part.getContent();
                } else if (part.isMimeType("text/html") && html == null) {
                    html = (String) part.getContent();
                }
            }
            return new String[]{plain, html};
        }

        return new String[]{null, null};
    }
}
