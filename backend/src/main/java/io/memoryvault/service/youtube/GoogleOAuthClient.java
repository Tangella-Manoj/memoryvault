package io.memoryvault.service.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Talks to Google's OAuth 2.0 endpoints directly over HTTP rather than pulling in the
 * full google-api-client SDK — the OAuth surface used here (authorization URL, code
 * exchange, refresh) is three well-documented REST calls, not worth a heavyweight
 * dependency for.
 */
@Component
public class GoogleOAuthClient {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleOAuthClient(
            @Value("${app.google.client-id:}") String clientId,
            @Value("${app.google.client-secret:}") String clientSecret,
            @Value("${app.google.redirect-uri}") String redirectUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public String buildAuthorizationUrl(String state) {
        return AUTH_URL + "?" +
                "client_id=" + encode(clientId) +
                "&redirect_uri=" + encode(redirectUri) +
                "&response_type=code" +
                "&scope=" + encode(SCOPE) +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + encode(state);
    }

    public record TokenResult(String accessToken, String refreshToken, Instant expiresAt) {
    }

    public TokenResult exchangeCode(String code) throws Exception {
        String body = "code=" + encode(code)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(redirectUri)
                + "&grant_type=authorization_code";
        JsonNode json = post(body);

        return new TokenResult(
                json.path("access_token").asText(),
                json.path("refresh_token").asText(null),
                Instant.now().plusSeconds(json.path("expires_in").asLong(3600))
        );
    }

    public TokenResult refresh(String refreshToken) throws Exception {
        String body = "refresh_token=" + encode(refreshToken)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&grant_type=refresh_token";
        JsonNode json = post(body);

        return new TokenResult(
                json.path("access_token").asText(),
                refreshToken, // Google doesn't rotate the refresh token on a refresh call.
                Instant.now().plusSeconds(json.path("expires_in").asLong(3600))
        );
    }

    private JsonNode post(String formBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Google OAuth token endpoint returned " + response.statusCode() + ": " + response.body());
        }
        return json;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
