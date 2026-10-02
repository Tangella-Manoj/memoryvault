package io.memoryvault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.memoryvault.domain.PasswordResetToken;
import io.memoryvault.domain.User;
import io.memoryvault.repository.PasswordResetTokenRepository;
import io.memoryvault.repository.UserRepository;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
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
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static String accessToken;
    private static String refreshToken;
    private static Long userId;
    private static String vaultEmail;
    private static Long savedItemId;
    private static String chromeToken;
    private static String attackerToken;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @Order(1)
    void live_healthCheck_returnsHttp200AndUpStatus() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("UP");

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

        User user = userRepository.findById(userId).orElseThrow();
        vaultEmail = user.getVaultEmail();
        assertThat(vaultEmail).isNotBlank();
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

        JsonNode data = json.get("data");
        assertThat(data.has("resetCode")).isFalse();

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
        savedItemId = saveJson.get("data").get("id").asLong();
        assertThat(savedItemId).isPositive();

        HttpEntity<Void> listRequest = new HttpEntity<>(authHeaders);
        ResponseEntity<String> listResponse = restTemplate.exchange(
                url("/api/vault?page=0&size=10"), HttpMethod.GET, listRequest, String.class
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listJson = objectMapper.readTree(listResponse.getBody());
        assertThat(listJson.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(listJson.get("data").get("content")).isNotEmpty();

        ResponseEntity<String> analyticsResponse = restTemplate.exchange(
                url("/api/vault/analytics"), HttpMethod.GET, listRequest, String.class
        );

        assertThat(analyticsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode analyticsJson = objectMapper.readTree(analyticsResponse.getBody());
        assertThat(analyticsJson.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(analyticsJson.get("data").get("totalItems").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(10)
    void live_vaultItem_getById_incrementsViewCount() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(authHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/vault/" + savedItemId), HttpMethod.GET, request, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(json.get("data").get("id").asLong()).isEqualTo(savedItemId);
        assertThat(json.get("data").get("viewCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(11)
    void live_vaultItem_typeMismatchAnd404_handledGracefully() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(authHeaders);

        // String passed for numeric ID -> TYPE_MISMATCH 400
        ResponseEntity<String> mismatchResponse = restTemplate.exchange(
                url("/api/vault/invalid-id-string"), HttpMethod.GET, request, String.class
        );
        assertThat(mismatchResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode mismatchJson = objectMapper.readTree(mismatchResponse.getBody());
        assertThat(mismatchJson.get("errorCode").asText()).isEqualTo("TYPE_MISMATCH");

        // Non-existent ID -> 404 RESOURCE_NOT_FOUND
        ResponseEntity<String> notFoundResponse = restTemplate.exchange(
                url("/api/vault/9999999"), HttpMethod.GET, request, String.class
        );
        assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode notFoundJson = objectMapper.readTree(notFoundResponse.getBody());
        assertThat(notFoundJson.get("errorCode").asText()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @Order(12)
    void live_crossUser_forbiddenAccessEnforced() throws Exception {
        // Register a second user (attacker)
        Map<String, String> attackerBody = Map.of(
                "email", "attacker@memoryvault.dev",
                "password", "AttackerPass@123",
                "displayName", "Malicious User"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> attackerReq = new HttpEntity<>(attackerBody, headers);

        ResponseEntity<String> regResponse = restTemplate.postForEntity(url("/api/auth/register"), attackerReq, String.class);
        assertThat(regResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode regJson = objectMapper.readTree(regResponse.getBody());
        attackerToken = regJson.get("data").get("accessToken").asText();

        HttpHeaders attackerHeaders = new HttpHeaders();
        attackerHeaders.setBearerAuth(attackerToken);
        HttpEntity<Void> request = new HttpEntity<>(attackerHeaders);

        // Attacker attempts to GET user 1's saved item -> 403 FORBIDDEN
        ResponseEntity<String> getResponse = restTemplate.exchange(
                url("/api/vault/" + savedItemId), HttpMethod.GET, request, String.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode getJson = objectMapper.readTree(getResponse.getBody());
        assertThat(getJson.get("errorCode").asText()).isEqualTo("FORBIDDEN");

        // Attacker attempts to rediscover user 1's saved item -> 403 FORBIDDEN
        ResponseEntity<String> rediscoverResponse = restTemplate.exchange(
                url("/api/vault/" + savedItemId + "/rediscover"), HttpMethod.POST, request, String.class
        );
        assertThat(rediscoverResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode rediscoverJson = objectMapper.readTree(rediscoverResponse.getBody());
        assertThat(rediscoverJson.get("errorCode").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    @Order(13)
    void live_vaultSearch_and_resurface_endpoints() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(authHeaders);

        // Blank query should return 400 BLANK_QUERY
        ResponseEntity<String> blankResponse = restTemplate.exchange(
                url("/api/vault/search?q="), HttpMethod.GET, request, String.class
        );
        assertThat(blankResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Valid search query
        ResponseEntity<String> searchResponse = restTemplate.exchange(
                url("/api/vault/search?q=hacker+news"), HttpMethod.GET, request, String.class
        );
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode searchJson = objectMapper.readTree(searchResponse.getBody());
        assertThat(searchJson.get("status").asText()).isEqualTo("SUCCESS");

        // Resurface feed
        ResponseEntity<String> resurfaceResponse = restTemplate.exchange(
                url("/api/vault/resurface?limit=5"), HttpMethod.GET, request, String.class
        );
        assertThat(resurfaceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Forgotten gems
        ResponseEntity<String> forgottenResponse = restTemplate.exchange(
                url("/api/vault/forgotten"), HttpMethod.GET, request, String.class
        );
        assertThat(forgottenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Legitimate owner rediscover
        ResponseEntity<String> rediscoverResponse = restTemplate.exchange(
                url("/api/vault/" + savedItemId + "/rediscover"), HttpMethod.POST, request, String.class
        );
        assertThat(rediscoverResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(14)
    void live_shareTarget_savesUrlSuccessfully() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBearerAuth(accessToken);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("url", "https://developer.mozilla.org/en-US/");
        formData.add("title", "MDN Web Docs");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/vault/share-target"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(json.get("data").get("url").asText()).isEqualTo("https://developer.mozilla.org/en-US/");

        // Missing URL in share payload -> 400 NO_URL_FOUND
        MultiValueMap<String, String> emptyForm = new LinkedMultiValueMap<>();
        emptyForm.add("text", "No links in here at all");
        HttpEntity<MultiValueMap<String, String>> badRequest = new HttpEntity<>(emptyForm, headers);
        ResponseEntity<String> badResponse = restTemplate.postForEntity(url("/api/vault/share-target"), badRequest, String.class);

        assertThat(badResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode badJson = objectMapper.readTree(badResponse.getBody());
        assertThat(badJson.get("errorCode").asText()).isEqualTo("NO_URL_FOUND");
    }

    @Test
    @Order(15)
    void live_digestToday_returnsDigestResponse() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(authHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/vault/digest/today"), HttpMethod.GET, request, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(json.get("data")).isNotNull();
    }

    @Test
    @Order(16)
    void live_userProfile_returnsMeEndpoint() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(authHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/users/me"), HttpMethod.GET, request, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        JsonNode data = json.get("data");
        assertThat(data.get("email").asText()).isEqualTo("livetester@memoryvault.dev");
        assertThat(data.get("vaultEmail").asText()).isEqualTo(vaultEmail);
    }

    @Test
    @Order(17)
    void live_chromeSession_and_quickSave_withChromeTokenHeader() throws Exception {
        // Create chrome session
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> sessionReq = new HttpEntity<>(authHeaders);

        ResponseEntity<String> sessionResponse = restTemplate.postForEntity(url("/api/chrome/session"), sessionReq, String.class);
        assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sessionJson = objectMapper.readTree(sessionResponse.getBody());
        chromeToken = sessionJson.get("data").get("chromeToken").asText();
        assertThat(chromeToken).isNotBlank();

        // Use X-Chrome-Token header directly without Bearer token
        HttpHeaders chromeHeaders = new HttpHeaders();
        chromeHeaders.setContentType(MediaType.APPLICATION_JSON);
        chromeHeaders.set("X-Chrome-Token", chromeToken);

        Map<String, Object> quickBody = Map.of(
                "url", "https://spring.io/projects/spring-boot",
                "source", "CHROME_EXTENSION"
        );
        HttpEntity<Map<String, Object>> quickReq = new HttpEntity<>(quickBody, chromeHeaders);

        ResponseEntity<String> quickResponse = restTemplate.postForEntity(url("/api/chrome/save/quick"), quickReq, String.class);
        assertThat(quickResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode quickJson = objectMapper.readTree(quickResponse.getBody());
        assertThat(quickJson.get("status").asText()).isEqualTo("SUCCESS");

        // Chrome contextual search
        HttpEntity<Void> searchReq = new HttpEntity<>(chromeHeaders);
        ResponseEntity<String> searchResponse = restTemplate.exchange(
                url("/api/chrome/search?q=spring"), HttpMethod.GET, searchReq, String.class
        );
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Chrome selection save
        Map<String, String> selectionBody = Map.of(
                "url", "https://spring.io/projects/spring-boot",
                "selectedText", "Spring Boot makes it easy to create stand-alone production-grade applications"
        );
        HttpEntity<Map<String, String>> selectionReq = new HttpEntity<>(selectionBody, chromeHeaders);
        ResponseEntity<String> selectionResponse = restTemplate.postForEntity(url("/api/chrome/save/selection"), selectionReq, String.class);
        assertThat(selectionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(18)
    void live_notifications_preferencesAndLifecycle() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(accessToken);

        // Get initial preferences
        HttpEntity<Void> getReq = new HttpEntity<>(authHeaders);
        ResponseEntity<String> getPrefResponse = restTemplate.exchange(
                url("/api/notifications/preferences"), HttpMethod.GET, getReq, String.class
        );
        assertThat(getPrefResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Subscribe push notification
        Map<String, String> subBody = Map.of("subscription", "{\"endpoint\":\"https://fcm.googleapis.com/test\"}");
        HttpEntity<Map<String, String>> subReq = new HttpEntity<>(subBody, authHeaders);
        ResponseEntity<String> subResponse = restTemplate.postForEntity(url("/api/notifications/subscribe"), subReq, String.class);
        assertThat(subResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode subJson = objectMapper.readTree(subResponse.getBody());
        assertThat(subJson.get("data").get("notificationsEnabled").asBoolean()).isTrue();

        // Recalculate optimal hour
        ResponseEntity<String> recalcResponse = restTemplate.postForEntity(url("/api/notifications/recalculate"), subReq, String.class);
        assertThat(recalcResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Unsubscribe
        ResponseEntity<String> unsubResponse = restTemplate.postForEntity(url("/api/notifications/unsubscribe"), subReq, String.class);
        assertThat(unsubResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(19)
    void live_plugins_listToggleAndTest() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> req = new HttpEntity<>(authHeaders);

        // List plugins
        ResponseEntity<String> listResponse = restTemplate.exchange(
                url("/api/plugins"), HttpMethod.GET, req, String.class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listJson = objectMapper.readTree(listResponse.getBody());
        assertThat(listJson.get("data").isArray()).isTrue();
        assertThat(listJson.get("data").size()).isGreaterThanOrEqualTo(8);

        // Toggle GitHub plugin
        Map<String, Object> toggleBody = Map.of("enabled", true);
        HttpEntity<Map<String, Object>> toggleReq = new HttpEntity<>(toggleBody, authHeaders);
        ResponseEntity<String> toggleResponse = restTemplate.postForEntity(
                url("/api/plugins/github/toggle"), toggleReq, String.class
        );
        assertThat(toggleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode toggleJson = objectMapper.readTree(toggleResponse.getBody());
        assertThat(toggleJson.get("data").get("enabled").asBoolean()).isTrue();

        // Test plugin connection
        ResponseEntity<String> testResponse = restTemplate.postForEntity(
                url("/api/plugins/github/test"), toggleReq, String.class
        );
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode testJson = objectMapper.readTree(testResponse.getBody());
        assertThat(testJson.get("data").get("success").asBoolean()).isTrue();

        // Trigger manual sync
        ResponseEntity<String> syncResponse = restTemplate.postForEntity(
                url("/api/plugins/github/sync"), toggleReq, String.class
        );
        assertThat(syncResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(20)
    void live_integrations_youtubeStatus() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        HttpEntity<Void> req = new HttpEntity<>(authHeaders);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/integrations/youtube/status"), HttpMethod.GET, req, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("data").get("connected").asBoolean()).isFalse();
    }

    @Test
    @Order(21)
    void live_bulkImport_and_statusLifecycle() throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(accessToken);

        Map<String, Object> importBody = Map.of(
                "urls", List.of("https://vuejs.org", "https://angular.dev")
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(importBody, authHeaders);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/vault/import/json"), request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        Long jobId = json.get("data").get("id").asLong();
        assertThat(jobId).isPositive();

        HttpEntity<Void> statusReq = new HttpEntity<>(authHeaders);
        ResponseEntity<String> statusResponse = restTemplate.exchange(
                url("/api/vault/import/status/" + jobId), HttpMethod.GET, statusReq, String.class
        );
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode statusJson = objectMapper.readTree(statusResponse.getBody());
        assertThat(statusJson.get("data").get("total").asInt()).isEqualTo(2);
    }

    @Test
    @Order(22)
    void live_emailInbound_ingestsForwardedLinks() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("recipient", vaultEmail);
        formData.add("subject", "Interesting Read");
        formData.add("body-plain", "Take a look at this article: https://news.google.com/home");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/email/inbound"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(json.get("data").get("itemsCreated").asInt()).isEqualTo(1);
    }

    @Test
    @Order(23)
    void live_passwordResetCycle_completesAndUpdatesCredentials() throws Exception {
        // Seed a known reset code into the repository for our test user
        String knownCode = "654321";
        PasswordResetToken token = passwordResetTokenRepository
                .findTopByUser_EmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc("livetester@memoryvault.dev")
                .orElseThrow();
        token.setCodeHash(sha256(knownCode));
        passwordResetTokenRepository.save(token);

        // Perform reset password
        Map<String, String> resetBody = Map.of(
                "email", "livetester@memoryvault.dev",
                "token", knownCode,
                "newPassword", "BrandNewPassword@999"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> resetReq = new HttpEntity<>(resetBody, headers);

        ResponseEntity<String> resetResponse = restTemplate.postForEntity(url("/api/auth/reset-password"), resetReq, String.class);
        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode resetJson = objectMapper.readTree(resetResponse.getBody());
        assertThat(resetJson.get("status").asText()).isEqualTo("SUCCESS");

        // Old password must fail
        Map<String, String> oldLoginBody = Map.of(
                "email", "livetester@memoryvault.dev",
                "password", "Password@123"
        );
        ResponseEntity<String> oldLoginResponse = restTemplate.postForEntity(url("/api/auth/login"), new HttpEntity<>(oldLoginBody, headers), String.class);
        assertThat(oldLoginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // New password must succeed
        Map<String, String> newLoginBody = Map.of(
                "email", "livetester@memoryvault.dev",
                "password", "BrandNewPassword@999"
        );
        ResponseEntity<String> newLoginResponse = restTemplate.postForEntity(url("/api/auth/login"), new HttpEntity<>(newLoginBody, headers), String.class);
        assertThat(newLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode newLoginJson = objectMapper.readTree(newLoginResponse.getBody());
        String newRefreshToken = newLoginJson.get("data").get("refreshToken").asText();

        // Logout revokes refresh token
        Map<String, String> logoutBody = Map.of("refreshToken", newRefreshToken);
        ResponseEntity<String> logoutResponse = restTemplate.postForEntity(url("/api/auth/logout"), new HttpEntity<>(logoutBody, headers), String.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Revoked token refresh must be rejected with 401
        ResponseEntity<String> rejectedRefresh = restTemplate.postForEntity(url("/api/auth/refresh"), new HttpEntity<>(logoutBody, headers), String.class);
        assertThat(rejectedRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
