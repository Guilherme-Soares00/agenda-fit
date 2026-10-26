package br.com.fiap.agendafit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import br.com.fiap.agendafit.repository.UserRepository;
import br.com.fiap.agendafit.security.CurrentUser;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestConstructor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AuthHttpTest {

    private final Environment environment;
    private final CurrentUser currentUser;
    private final JsonMapper jsonMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void registrationCreatesUserWithNormalizedEmailAndHashedPassword() throws Exception {
        var response = request("POST", "/auth/register", Map.of(
                "name", "Nova Pessoa", "email", "NOVA@FIT.COM", "password", "segredo123", "role", "ADMIN"), null);

        assertThat(response.statusCode()).isEqualTo(201);
        var body = jsonMapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.get("name").asString()).isEqualTo("Nova Pessoa");
        assertThat(body.get("email").asString()).isEqualTo("nova@fit.com");
        assertThat(body.get("role").asString()).isEqualTo("USER");
        assertThat(body.has("password")).isFalse();
        var saved = userRepository.findById(body.get("id").asLong()).orElseThrow();
        assertThat(saved.getPassword()).startsWith("$2").isNotEqualTo("segredo123");
        assertThat(passwordEncoder.matches("segredo123", saved.getPassword())).isTrue();
    }

    @Test
    void registrationRejectsInvalidInput() throws Exception {
        for (var payload : List.of(
                Map.of("name", " ", "email", "valid@fit.com", "password", "segredo123"),
                Map.of("name", "Nome", "email", "invalid", "password", "segredo123"),
                Map.of("name", "Nome", "email", "valid@fit.com", "password", " "),
                Map.of("name", "Nome", "email", "valid@fit.com"),
                Map.of("name", "Nome", "email", "valid@fit.com", "password", "á".repeat(37)))) {
            var response = request("POST", "/auth/register", payload, null);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(jsonMapper.readTree(response.body()).get("error").asString()).isNotBlank();
        }
    }

    @Test
    void registrationRejectsDuplicateEmailWithoutCaseSensitivity() throws Exception {
        var response = request("POST", "/auth/register", Map.of(
                "name", "Outro Nome", "email", "ANA@FIT.COM", "password", "segredo123"), null);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(jsonMapper.readTree(response.body()).get("error").asString()).isNotBlank();
    }

    @Test
    void loginIssuesVerifiableHs256TokensForSeededUsers() throws Exception {
        for (var credentials : List.of(
                Map.of("email", "ADMIN@FIT.COM", "password", "admin123"),
                Map.of("email", "ana@fit.com", "password", "ana123"),
                Map.of("email", "bruno@fit.com", "password", "bruno123"))) {
            var response = request("POST", "/auth/login", credentials, null);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            var body = jsonMapper.readTree(response.body());
            assertThat(body.size()).isEqualTo(2);
            assertThat(body.get("expiresIn").isNumber()).isTrue();
            assertThat(body.get("expiresIn").asLong()).isEqualTo(3600);
            var jwt = SignedJWT.parse(body.get("token").asString());
            assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("HS256");
            assertThat(jwt.verify(new MACVerifier(
                    environment.getRequiredProperty("jwt.secret").getBytes(StandardCharsets.UTF_8)))).isTrue();
            var claims = jwt.getJWTClaimsSet();
            String email = credentials.get("email").toLowerCase(Locale.ROOT);
            var user = userRepository.findByEmail(email).orElseThrow();
            assertThat(claims.getSubject()).isEqualTo(email);
            assertThat(claims.getStringClaim("name")).isEqualTo(user.getName());
            assertThat(claims.getStringClaim("role")).isEqualTo(user.getRole().name());
            assertThat(claims.getExpirationTime().toInstant().getEpochSecond()
                    - claims.getIssueTime().toInstant().getEpochSecond()).isEqualTo(3600);
        }
    }

    @Test
    void loginUsesSameGenericErrorForUnknownEmailAndWrongPassword() throws Exception {
        var unknown = request("POST", "/auth/login", Map.of("email", "absent@fit.com", "password", "wrong123"), null);
        var wrong = request("POST", "/auth/login", Map.of("email", "ana@fit.com", "password", "wrong123"), null);

        assertThat(unknown.statusCode()).as(unknown.body()).isEqualTo(401);
        assertThat(wrong.statusCode()).as(wrong.body()).isEqualTo(401);
        assertThat(wrong.body()).isEqualTo(unknown.body());
        assertThat(jsonMapper.readTree(wrong.body()).get("error").asString()).isEqualTo("Invalid email or password");
    }

    @Test
    void loginValidatesRequiredFields() throws Exception {
        for (var payload : List.of(
                Map.of("email", "bad", "password", "123456"),
                Map.of("email", "ana@fit.com", "password", " "),
                Map.of("email", "ana@fit.com"))) {
            var response = request("POST", "/auth/login", payload, null);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertThat(jsonMapper.readTree(response.body()).get("error").asString()).isNotBlank();
        }
    }

    @Test
    void protectedEndpointsRejectAnonymousRequestsWithJsonWithoutCreatingSession() throws Exception {
        for (var route : List.of(
                Map.entry("POST", "/classes"), Map.entry("PUT", "/classes/1"), Map.entry("DELETE", "/classes/1"),
                Map.entry("POST", "/classes/1/enrollments"), Map.entry("GET", "/enrollments/me"),
                Map.entry("DELETE", "/enrollments/1"))) {
            var response = request(route.getKey(), route.getValue(), null, null);
            assertThat(response.statusCode()).as(route.toString()).isEqualTo(401);
            assertJsonSecurityError(response);
            assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        }
    }

    @Test
    void userCannotManageClasses() throws Exception {
        String token = login("ana@fit.com", "ana123");
        for (var route : List.of(Map.entry("POST", "/classes"),
                Map.entry("PUT", "/classes/1"), Map.entry("DELETE", "/classes/1"))) {
            var response = request(route.getKey(), route.getValue(), Map.of(), token);
            assertThat(response.statusCode()).as(route.toString()).isEqualTo(403);
            assertJsonSecurityError(response);
            assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        }
    }

    @Test
    void administratorCanManageClassesUsingBearerAuthentication() throws Exception {
        String token = login("admin@fit.com", "admin123");
        var payload = Map.of("title", "Aula de teste", "modality", "YOGA", "level", "BEGINNER",
                "instructor", "Instrutora", "startsAt", LocalDateTime.now().plusDays(30).toString(),
                "durationMinutes", 60, "capacity", 20);
        var created = request("POST", "/classes", payload, token);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        long id = jsonMapper.readTree(created.body()).get("id").asLong();
        assertThat(request("PUT", "/classes/" + id, payload, token).statusCode()).isEqualTo(200);
        assertThat(request("DELETE", "/classes/" + id, null, token).statusCode()).isEqualTo(204);
    }

    @Test
    void bearerTokenIsValidatedOnPublicEndpoints() throws Exception {
        var response = request("GET", "/classes", null, "not-a-jwt");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
        assertJsonSecurityError(response);
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void unmatchedRoutesAreDenied() throws Exception {
        String token = login("admin@fit.com", "admin123");
        for (var route : List.of(Map.entry("GET", "/not-allowed"), Map.entry("GET", "/auth/login"),
                Map.entry("POST", "/logout"), Map.entry("POST", "/v3/api-docs"),
                Map.entry("PATCH", "/classes/1"), Map.entry("PUT", "/classes/1/enrollments"))) {
            var response = request(route.getKey(), route.getValue(), null, token);
            assertThat(response.statusCode()).as(route.toString()).isEqualTo(403);
            assertJsonSecurityError(response);
        }
    }

    @Test
    void publicClassReadsAndDocumentationRemainAvailable() throws Exception {
        for (String path : List.of("/classes", "/classes/1", "/v3/api-docs", "/swagger-ui/index.html")) {
            var response = request("GET", path, null, null);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
        }
        assertThat(request("GET", "/swagger-ui.html", null, null).statusCode()).isEqualTo(302);
    }

    @Test
    void currentUserReflectsCurrentSecurityContextWithoutDatabaseLookups() {
        try {
            SecurityContextHolder.clearContext();
            assertThat(currentUser.email()).isEqualTo(Optional.empty());
            assertThat(currentUser.isAuthenticated()).isEqualTo(false);
            assertThat(currentUser.isAdmin()).isEqualTo(false);
            var jwt = Jwt.withTokenValue("test")
                    .header("alg", "HS256").subject("ana@fit.com").build();
            var context = SecurityContextHolder.getContext();
            context.setAuthentication(new JwtAuthenticationToken(
                    jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            assertThat(currentUser.email()).isEqualTo(Optional.of("ana@fit.com"));
            assertThat(currentUser.isAuthenticated()).isEqualTo(true);
            assertThat(currentUser.isAdmin()).isEqualTo(false);
            context.setAuthentication(new JwtAuthenticationToken(
                    jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertThat(currentUser.isAdmin()).isEqualTo(true);
            context.setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("ana@fit.com", "password"));
            assertThat(currentUser.email()).isEqualTo(Optional.empty());
            assertThat(currentUser.isAuthenticated()).isFalse();
            assertThat(currentUser.isAdmin()).isFalse();
            context.setAuthentication(new AnonymousAuthenticationToken(
                    "key", "anonymousUser", List.of(
                    new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            assertThat(currentUser.email()).isEqualTo(Optional.empty());
            assertThat(currentUser.isAuthenticated()).isEqualTo(false);
            assertThat(currentUser.isAdmin()).isEqualTo(false);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void authenticationDocumentationListsSuccessAndErrorResponses() throws Exception {
        var response = request("GET", "/v3/api-docs", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var paths = jsonMapper.readTree(response.body()).get("paths");
        for (String path : List.of("/auth/register", "/auth/login")) {
            var operation = paths.get(path).get("post");
            assertThat(operation.has("summary")).as(path + " summary").isTrue();
            assertThat(operation.get("summary").asString()).isNotBlank();
            assertThat(operation.get("tags").get(0).asString()).isEqualTo("Authentication");
            var responses = operation.get("responses");
            for (String status : List.of("400", "401", "403", "404", "409")) {
                assertThat(responses.has(status)).as(path + " " + status).isTrue();
                var content = responses.path(status).path("content");
                for (String mediaType : content.propertyNames()) {
                    assertThat(content.path(mediaType).path("schema").path("$ref").asString())
                            .as(path + " " + status + " " + mediaType).endsWith("/ErrorResponse");
                }
            }
            for (String status : List.of("400", "401", "403", "404", "409")) {
                var content = responses.path(status).path("content");
                assertThat(content.propertyNames()).as(path + " " + status + " content types")
                        .contains("application/json")
                        .allMatch(mediaType -> mediaType.equals("application/json") || mediaType.equals("application/hal+json"));
            }
            String success = path.endsWith("register") ? "201" : "200";
            assertThat(responses.has(success)).isTrue();
            for (var representation : responses.path(success).path("content")) {
                assertThat(representation.path("schema").path("$ref").asString())
                        .endsWith(path.endsWith("register") ? "/UserResponse" : "/LoginResponse");
            }
        }
    }

    @Test
    void loginRejectsPasswordsExceedingBcryptUtf8LimitWithoutServerError() throws Exception {
        var response = request("POST", "/auth/login", Map.of("email", "ana@fit.com", "password", "á".repeat(37)), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(401);
        assertThat(jsonMapper.readTree(response.body()).get("error").asString()).isEqualTo("Invalid email or password");
    }

    @Test
    void registrationAcceptsExactly72Utf8BytesWithoutPasswordTruncation() throws Exception {
        String password = "á".repeat(36);
        var response = request("POST", "/auth/register", Map.of(
                "name", "Limite", "email", "limite@fit.com", "password", password), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(login("limite@fit.com", password)).isNotBlank();
        var tooLong = request("POST", "/auth/login", Map.of("email", "limite@fit.com", "password", password + "x"), null);
        assertThat(tooLong.statusCode()).as(tooLong.body()).isEqualTo(401);
    }

    @Test
    void concurrentRegistrationsHaveOnlyOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        var payload = Map.of("name", "Concorrente", "email", "concurrent@fit.com", "password", "segredo123");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await();
                return request("POST", "/auth/register", payload, null);
            });
            var second = executor.submit(() -> {
                start.await();
                return request("POST", "/auth/register", payload, null);
            });
            start.countDown();
            var responses = List.of(first.get(), second.get());
            assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201, 409);
            assertThat(userRepository.findByEmail("concurrent@fit.com")).isPresent();
            assertJsonSecurityError(responses.stream().filter(response -> response.statusCode() == 409).findFirst().orElseThrow());
        }
    }

    @Test
    void expiredAndTamperedJwtTokensAreRejected() throws Exception {
        String valid = login("admin@fit.com", "admin123");
        var parts = valid.split("\\.");
        char firstSignatureCharacter = parts[2].charAt(0);
        String tampered = parts[0] + "." + parts[1] + "."
                + (firstSignatureCharacter == 'a' ? 'b' : 'a') + parts[2].substring(1);
        var claims = new JWTClaimsSet.Builder().subject("admin@fit.com")
                .claim("role", "ADMIN").issueTime(Date.from(Instant.now().minusSeconds(7200)))
                .expirationTime(Date.from(Instant.now().minusSeconds(120))).build();
        var expired = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        expired.sign(new MACSigner(environment.getRequiredProperty("jwt.secret").getBytes(StandardCharsets.UTF_8)));
        for (String token : List.of(tampered, expired.serialize())) {
            var response = request("GET", "/classes", null, token);
            assertThat(response.statusCode()).isEqualTo(401);
            assertJsonSecurityError(response);
        }
    }

    @Test
    void authenticationDoesNotPersistAcrossRequests() throws Exception {
        var authenticated = request("GET", "/classes", null, login("admin@fit.com", "admin123"));
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(authenticated.headers().allValues("Set-Cookie")).isEmpty();
        var anonymous = request("POST", "/classes", Map.of(), null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void corsPreflightDoesNotRequireAuthentication() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"
                        + environment.getRequiredProperty("local.server.port") + "/classes"))
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("*");
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods")).contains("POST");
    }

    private String login(String email, String password) throws Exception {
        var response = request("POST", "/auth/login", Map.of("email", email, "password", password), null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return jsonMapper.readTree(response.body()).get("token").asString();
    }

    private void assertJsonSecurityError(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        var body = jsonMapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get("error").asString()).isNotBlank();
    }

    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:"
                + environment.getRequiredProperty("local.server.port") + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
