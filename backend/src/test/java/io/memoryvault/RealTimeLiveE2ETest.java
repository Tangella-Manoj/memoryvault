package io.memoryvault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.memoryvault.domain.PasswordResetToken;
import io.memoryvault.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealTimeLiveE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static String accessToken;
    private static String refreshToken;
    private static Long userId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @Order(1)
    void live_healthCheck_returnsHttp200AndUpStatus() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("UP");

        // Verify HTTP security headers enforced by SecurityConfig
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @Order(2)
    void live_register_createsUserAndIssuesTokens() throws Exception {
        Map<String, String> body = Map.of(
                "email", "  LiveTester@MemoryVault.DEV  ",
                "password", "Password@123",
                "displayName", "Live Real-Time User"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/auth/register"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");

        JsonNode data = json.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("email").asText()).isEqualTo("livetester@memoryvault.dev");
        assertThat(data.get("displayName").asText()).isEqualTo("Live Real-Time User");

        accessToken = data.get("accessToken").asText();
        refreshToken = data.get("refreshToken").asText();
        userId = data.get("userId").asLong();

        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(userId).isPositive();
    }

    @Test
    @Order(3)
    void live_login_withWrongPassword_returns401AndInvalidPasswordCode() throws Exception {
        Map<String, String> body = Map.of(
                "email", "livetester@memoryvault.dev",
                "password", "WrongPassword@999"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/auth/login"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("ERROR");
        assertThat(json.get("errorCode").asText()).isEqualTo("INVALID_PASSWORD");
    }

    @Test
    @Order(4)
    void live_login_withNonExistentEmail_returns401AndUserNotFoundCode() throws Exception {
        Map<String, String> body = Map.of(
                "email", "nobody@memoryvault.dev",
                "password", "AnyPassword@123"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/auth/login"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("ERROR");
        assertThat(json.get("errorCode").asText()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @Order(5)
    void live_login_withCorrectCredentials_succeeds() throws Exception {
        Map<String, String> body = Map.of(
                "email", "livetester@memoryvault.dev",
                "password", "Password@123"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/auth/login"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        JsonNode data = json.get("data");
        accessToken = data.get("accessToken").asText();
        refreshToken = data.get("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(6)
    void live_forgotPassword_generatesHashedTokenInDbAndNoCodeInResponse() throws Exception {
        Map<String, String> body = Map.of("email", "livetester@memoryvault.dev");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/auth/forgot-password"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");

        // CRITICAL: Plain code must NOT be leaked in response
        JsonNode data = json.get("data");
        assertThat(data.has("resetCode")).isFalse();

        // Verify token saved in DB
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository
                .findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc("livetester@memoryvault.dev");
        assertThat(tokenOpt).isPresent();
        assertThat(tokenOpt.get().getCodeHash()).hasSize(64);
        assertThat(tokenOpt.get().isUsed()).isFalse();
    }

    @Test
    @Order(7)
    void live_refresh_rotatesTokenSuccessfully() throws Exception {
        Map<String, String> body = Map.of("refreshToken", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/auth/refresh"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");

        JsonNode data = json.get("data");
        accessToken = data.get("accessToken").asText();
        refreshToken = data.get("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
    }

    @Test
    @Order(8)
    void live_unauthenticatedVaultRequest_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/vault"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(9)
    void live_authenticatedSaveAndListVault_succeeds() throws Exception {
        // Save an item
        Map<String, Object> saveBody = Map.of(
                "url", "https://news.ycombinator.com/item?id=40000000",
                "source", "WEB"
        );

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> saveRequest = new HttpEntity<>(saveBody, authHeaders);
        ResponseEntity<String> saveResponse = restTemplate.postForEntity(url("/api/vault/save"), saveRequest, String.class);

        assertThat(saveResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode saveJson = objectMapper.readTree(saveResponse.getBody());
        assertThat(saveJson.get("status").asText()).isEqualTo("SUCCESS");
        Long itemId = saveJson.get("data").get("id").asLong();
        assertThat(itemId).isPositive();

        // List vault items
        HttpEntity<Void> listRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<String> listResponse = restTemplate.exchange(
                url("/api/vault?page=0&size=10"), HttpMethod.GET, listRequest, String.class
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listJson = objectMapper.readTree(listResponse.getBody());
        assertThat(listJson.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(listJson.get("data").get("content")).isNotEmpty();

        // Get Analytics
        ResponseEntity<String> analyticsResponse = restTemplate.exchange(
                url("/api/vault/analytics"), HttpMethod.GET, listRequest, String.class
        );

        assertThat(analyticsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode analyticsJson = objectMapper.readTree(analyticsResponse.getBody());
        assertThat(analyticsJson.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(analyticsJson.get("data").get("totalItems").asLong()).isGreaterThanOrEqualTo(1);
    }
}
