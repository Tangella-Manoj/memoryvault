package io.memoryvault.service;

import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.domain.enums.LifeContext;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.VaultItemSavedEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an inbound email (from either the self-hosted SMTP receiver or the Mailgun
 * webhook — see {@link SmtpReceiverConfig} and {@code EmailInboundController}) into vault
 * items. Every URL found in the body becomes its own item; there is no single "the" link
 * for a forwarded newsletter or WhatsApp message, which is the whole point of this
 * ingestion channel.
 */
@Service
public class EmailIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EmailIngestionService.class);
    private static final int MIN_URL_LENGTH = 15;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>)]+");
    private static final Pattern NOISE_PATTERN = Pattern.compile(
            "unsubscribe|opt-?out|track|pixel|beacon|\\.gif($|\\?)|\\.png($|\\?)|1x1", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final VaultItemRepository vaultItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EmailIngestionService(
            UserRepository userRepository,
            VaultItemRepository vaultItemRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.vaultItemRepository = vaultItemRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param recipientAddress the email's To address, matched against {@code users.vault_email}
     * @param subject          the email subject, used as each created item's title
     * @param plainBody        the email's {@code text/plain} part, if present
     * @param htmlBody         the email's {@code text/html} part, if present
     * @return number of vault items created; 0 if the recipient doesn't match any user or
     *         no qualifying URLs were found — never throws for a "no match" case, since
     *         a misdirected email is not a server error
     */
    @Transactional
    public int ingest(String recipientAddress, String subject, String plainBody, String htmlBody) {
        Optional<User> maybeUser = userRepository.findByVaultEmail(normalizeAddress(recipientAddress));
        if (maybeUser.isEmpty()) {
            log.warn("Inbound email to unknown vault address: {}", recipientAddress);
            return 0;
        }
        User user = maybeUser.get();

        Set<String> urls = new LinkedHashSet<>();
        urls.addAll(extractFromHtml(htmlBody));
        urls.addAll(extractFromText(plainBody));

        int created = 0;
        for (String url : urls) {
            if (!qualifies(url)) {
                continue;
            }
            VaultItem item = VaultItem.builder()
                    .user(user)
                    .url(url)
                    .title(subject)
                    .status(ItemStatus.PROCESSING)
                    .source(ItemSource.EMAIL)
                    .lifeContext(LifeContext.LEARNING)
                    .build();
            item = vaultItemRepository.save(item);
            eventPublisher.publishEvent(new VaultItemSavedEvent(item.getId()));
            created++;
        }

        log.info("Ingested {} URL(s) from email to {} (subject: {})", created, recipientAddress, subject);
        return created;
    }

    private boolean qualifies(String url) {
        if (url.length() < MIN_URL_LENGTH) {
            return false;
        }
        return !NOISE_PATTERN.matcher(url).find();
    }

    private Set<String> extractFromHtml(String html) {
        Set<String> urls = new LinkedHashSet<>();
        if (html == null || html.isBlank()) {
            return urls;
        }
        Document doc = Jsoup.parse(html);
        for (Element link : doc.select("a[href]")) {
            String href = link.attr("href");
            if (href.startsWith("http")) {
                urls.add(stripTrailingPunctuation(href));
            }
        }
        return urls;
    }

    private Set<String> extractFromText(String text) {
        Set<String> urls = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return urls;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            urls.add(stripTrailingPunctuation(matcher.group()));
        }
        return urls;
    }

    private String stripTrailingPunctuation(String url) {
        return url.replaceAll("[.,;:!?]+$", "");
    }

    private String normalizeAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase();
    }
}
