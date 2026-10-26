package br.com.fiap.agendafit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ClassHttpTest {

    private final Environment environment;
    private final jakarta.persistence.EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    ClassHttpTest(Environment environment, jakarta.persistence.EntityManagerFactory entityManagerFactory,
                  DataSource dataSource) {
        this.environment = environment;
        this.entityManagerFactory = entityManagerFactory;
        this.dataSource = dataSource;
    }

    @Test
    void listsDefaultPageInChronologicalOrder() throws Exception {
        HttpResponse<String> response = get("/classes");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("page").path("size").asInt()).isEqualTo(10);
        assertThat(body.path("page").path("number").asInt()).isZero();
        assertThat(body.path("page").path("totalElements").asLong()).isEqualTo(60);
        JsonNode classes = body.path("_embedded").path("classes");
        assertThat(classes.size()).isEqualTo(10);
        assertThat(classes.get(0).path("availableSpots").asInt()).isEqualTo(19);
        assertThat(classes.get(0).path("_links").has("self")).isTrue();
        assertThat(classes.get(0).path("_links").has("classes")).isTrue();
        assertThat(classes.get(0).path("_links").has("enroll")).isFalse();
        assertThat(body.path("_links").has("first")).isTrue();
        assertThat(body.path("_links").has("next")).isTrue();
        assertThat(body.path("_links").has("last")).isTrue();
        for (int index = 1; index < classes.size(); index++) {
            assertThat(classes.get(index).path("startsAt").asText())
                    .isGreaterThanOrEqualTo(classes.get(index - 1).path("startsAt").asText());
        }
    }

    @Test
    void filtersExamplesIgnoringNullsAndTextCase() throws Exception {
        HttpResponse<String> response = get("/classes/example?modality=YOGA&level=BEGINNER&instructor=carla%20mendes&size=1");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("page").path("totalElements").asInt()).isPositive();
        JsonNode first = body.path("_embedded").path("classes").get(0);
        assertThat(first.path("modality").asText()).isEqualTo("YOGA");
        assertThat(first.path("level").asText()).isEqualTo("BEGINNER");
        assertThat(first.path("instructor").asText()).isEqualToIgnoringCase("carla mendes");
        assertThat(body.path("_links").path("self").path("href").asText()).contains("instructor=carla%20mendes");
        assertThat(mapper.readTree(get("/classes/example").body()).path("page").path("totalElements").asInt()).isEqualTo(60);
        JsonNode empty = mapper.readTree(get("/classes/example?instructor=Nobody").body());
        assertThat(empty.has("_embedded")).isFalse();
    }

    @Test
    void searchesWithIndependentAndCombinedSpecifications() throws Exception {
        JsonNode all = mapper.readTree(get("/classes?size=100").body()).path("_embedded").path("classes");
        String boundary = all.get(0).path("startsAt").asText();
        String[] queries = {"", "title=YOg", "modality=PILATES", "level=ADVANCED",
                "startsAfter=" + boundary, "startsBefore=" + boundary, "availableOnly=true", "availableOnly=false",
                "title=YOg&modality=YOGA&level=BEGINNER&startsAfter=" + boundary + "&startsBefore=" + boundary + "&availableOnly=true"};
        for (String query : queries) {
            HttpResponse<String> response = get("/classes/search?size=100&" + query);
            assertThat(response.statusCode()).as(query).isEqualTo(200);
            int expected = 0;
            for (JsonNode item : all) {
                boolean matches = (!query.contains("title=") || item.path("title").asText().toLowerCase(java.util.Locale.ROOT).contains("yog"))
                        && (!query.contains("modality=PILATES") || item.path("modality").asText().equals("PILATES"))
                        && (!query.contains("modality=YOGA") || item.path("modality").asText().equals("YOGA"))
                        && (!query.contains("level=ADVANCED") || item.path("level").asText().equals("ADVANCED"))
                        && (!query.contains("level=BEGINNER") || item.path("level").asText().equals("BEGINNER"))
                        && (!query.contains("startsAfter=") || item.path("startsAt").asText().compareTo(boundary) >= 0)
                        && (!query.contains("startsBefore=") || item.path("startsAt").asText().compareTo(boundary) <= 0)
                        && (!query.contains("availableOnly=true") || item.path("availableSpots").asInt() > 0);
                if (matches) {
                    expected++;
                }
            }
            assertThat(mapper.readTree(response.body()).path("page").path("totalElements").asInt()).as(query).isEqualTo(expected);
        }
        JsonNode paged = mapper.readTree(get("/classes/search?title=YOg&availableOnly=true&size=1").body());
        assertThat(paged.path("_links").path("next").path("href").asText()).contains("title=YOg", "availableOnly=true", "page=1");
    }

    @Test
    void returnsClassDetailAsHalDto() throws Exception {
        HttpResponse<String> response = get("/classes/1");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("availableSpots").asInt()).isEqualTo(19);
        assertThat(body.path("_links").path("self").path("href").asText()).endsWith("/classes/1");
        java.util.Set<String> fields = new java.util.HashSet<>();
        body.propertyNames().forEach(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "title", "modality", "level", "instructor", "startsAt", "durationMinutes", "capacity", "availableSpots", "_links");
        assertThat(get("/classes/999999").statusCode()).isEqualTo(404);
    }

    @Test
    void returnsJsonNotFoundForUnmappedClassPaths() throws Exception {
        for (String path : new String[]{"/classes/1/extra", "/classes/"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertJsonHttpError(response, "Not Found");
        }
    }

    @Test
    void preservesMethodNotAllowedForUnsupportedClassMethod() throws Exception {
        HttpResponse<String> response = get("/classes/1/enrollments");
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow")).hasValueSatisfying(value -> assertThat(value).contains("POST"));
        assertJsonHttpError(response, "Method Not Allowed");
    }

    @Test
    void returnsJsonNotAcceptableForUnsupportedRepresentation() throws Exception {
        URI uri = URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/classes");
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).header("Accept", "text/plain").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(406);
        assertJsonHttpError(response, "Not Acceptable");
    }

    @Test
    void preservesUnsupportedMediaTypeForClassWrites() throws Exception {
        URI uri = URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/classes");
        HttpRequest request = HttpRequest.newBuilder(uri).header("Content-Type", "text/plain")
                .header("Authorization", "Bearer " + token("admin@fit.com", "ADMIN"))
                .POST(HttpRequest.BodyPublishers.ofString("private unsupported body")).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(415);
        assertJsonHttpError(response, "Unsupported Media Type");
    }

    @Test
    void boundsPaginationAndRejectsInvalidSorting() throws Exception {
        JsonNode bounded = mapper.readTree(get("/classes?size=1000").body());
        assertThat(bounded.path("page").path("size").asInt()).isEqualTo(100);
        for (String path : new String[]{"/classes?sort=missing,asc", "/classes/example?sort=missing", "/classes/search?sort=missing", "/classes/search?modality=INVALID", "/classes/search?startsAfter=not-a-date"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(400);
            JsonNode error = mapper.readTree(response.body());
            assertThat(error.size()).isEqualTo(1);
            assertThat(error.path("error").asText()).isNotBlank();
        }
    }

    @Test
    void documentsTheApiAndSearchParameters() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode document = mapper.readTree(response.body());
        assertThat(document.path("info").path("title").asText()).isEqualTo("Agenda Fit API");
        assertThat(document.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        JsonNode search = document.path("paths").path("/classes/search").path("get");
        assertThat(search.path("summary").asText()).isNotBlank();
        for (String status : new String[]{"400", "401", "403", "404", "409"}) {
            assertThat(search.path("responses").has(status)).as(status).isTrue();
        }
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    @Test
    void createsAndUpdatesClassDtosWithLocation() throws Exception {
        String token = token("admin@fit.com", "ADMIN");
        String payload = "{\"title\":\"Contract class\",\"modality\":\"YOGA\",\"level\":\"BEGINNER\",\"instructor\":\"Test Instructor\",\"startsAt\":\"2099-11-03T07:00:00\",\"durationMinutes\":60,\"capacity\":2}";
        HttpResponse<String> created = request("POST", "/classes", payload, token);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = mapper.readTree(created.body());
        String path = "/classes/" + body.path("id").asLong();
        try {
            assertThat(created.headers().firstValue("Location").orElseThrow()).endsWith(path);
            assertThat(body.path("availableSpots").asInt()).isEqualTo(2);
            assertThat(body.path("_links").has("self")).isTrue();
            HttpResponse<String> updated = request("PUT", path, payload.replace("Contract class", "Updated class"), token);
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(updated.body()).path("title").asText()).isEqualTo("Updated class");
            assertThat(mapper.readTree(updated.body()).path("_links").has("classes")).isTrue();
        } finally {
            assertThat(request("DELETE", path, null, token).statusCode()).isEqualTo(204);
        }
    }

    @Test
    void rejectsClassesScheduledInThePast() throws Exception {
        String payload = "{\"title\":\"Past class\",\"modality\":\"YOGA\",\"level\":\"BEGINNER\",\"instructor\":\"Test Instructor\",\"startsAt\":\"2000-01-01T07:00:00\",\"durationMinutes\":60,\"capacity\":2}";
        HttpResponse<String> response = request("POST", "/classes", payload, token("admin@fit.com", "ADMIN"));
        if (response.statusCode() == 201) {
            request("DELETE", "/classes/" + mapper.readTree(response.body()).path("id").asLong(), null, token("admin@fit.com", "ADMIN"));
        }
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(response.body()).path("error").asText()).isNotBlank();
    }

    @Test
    void enrollsAndCancelsOnlyOwnedEnrollments() throws Exception {
        String admin = token("admin@fit.com", "ADMIN");
        String ana = token("ana@fit.com", "USER");
        String bruno = token("bruno@fit.com", "USER");
        String payload = "{\"title\":\"Enrollment class\",\"modality\":\"PILATES\",\"level\":\"BEGINNER\",\"instructor\":\"Test Instructor\",\"startsAt\":\"2099-11-03T07:00:00\",\"durationMinutes\":60,\"capacity\":1}";
        JsonNode created = mapper.readTree(request("POST", "/classes", payload, admin).body());
        String path = "/classes/" + created.path("id").asLong();
        try {
            HttpResponse<String> response = request("POST", path + "/enrollments", null, ana);
            assertThat(response.statusCode()).isEqualTo(201);
            JsonNode enrollment = mapper.readTree(response.body());
            assertThat(enrollment.path("classId").asLong()).isEqualTo(created.path("id").asLong());
            assertThat(enrollment.path("classTitle").asText()).isEqualTo("Enrollment class");
            assertThat(enrollment.path("createdAt").asText()).isNotBlank();
            assertThat(enrollment.path("_links").path("class").path("href").asText()).endsWith(path);
            assertThat(enrollment.path("_links").has("cancel")).isTrue();
            assertThat(enrollment.has("user")).isFalse();
            assertThat(enrollment.has("gymClass")).isFalse();
            assertThat(request("POST", path + "/enrollments", null, ana).statusCode()).isEqualTo(409);
            assertThat(request("POST", path + "/enrollments", null, bruno).statusCode()).isEqualTo(409);
            JsonNode mine = mapper.readTree(request("GET", "/enrollments/me?size=100", null, ana).body());
            assertThat(mine.path("page").path("size").asInt()).isEqualTo(100);
            assertThat(mine.path("_embedded").path("enrollments").isArray()).isTrue();
            boolean found = false;
            for (JsonNode item : mine.path("_embedded").path("enrollments")) {
                if (item.path("id").asLong() == enrollment.path("id").asLong()) {
                    found = true;
                }
            }
            assertThat(found).isTrue();
            String cancellation = "/enrollments/" + enrollment.path("id").asLong();
            assertThat(request("DELETE", cancellation, null, bruno).statusCode()).isEqualTo(403);
            assertThat(request("DELETE", cancellation, null, ana).statusCode()).isEqualTo(204);
            assertThat(request("DELETE", cancellation, null, ana).statusCode()).isEqualTo(404);
            assertThat(mapper.readTree(get(path).body()).path("availableSpots").asInt()).isEqualTo(1);
        } finally {
            assertThat(request("DELETE", path, null, admin).statusCode()).isEqualTo(204);
        }
    }

    @Test
    void exposesConditionalLinksForEachAuthenticatedRequest() throws Exception {
        String admin = token("admin@fit.com", "ADMIN");
        String ana = token("ana@fit.com", "USER");
        String bruno = token("bruno@fit.com", "USER");
        JsonNode administrator = mapper.readTree(request("GET", "/classes", null, admin).body());
        assertThat(administrator.path("_links").has("create")).isTrue();
        JsonNode adminClass = administrator.path("_embedded").path("classes").get(0);
        assertThat(adminClass.path("_links").has("update")).isTrue();
        assertThat(adminClass.path("_links").has("delete")).isTrue();
        assertThat(adminClass.path("_links").has("enroll")).isTrue();
        JsonNode owner = mapper.readTree(request("GET", "/classes/1", null, ana).body());
        assertThat(owner.path("_links").has("enroll")).isFalse();
        assertThat(owner.path("_links").has("update")).isFalse();
        JsonNode notEnrolled = mapper.readTree(request("GET", "/classes/1", null, bruno).body());
        assertThat(notEnrolled.path("_links").has("enroll")).isTrue();
        JsonNode anonymous = mapper.readTree(get("/classes").body());
        assertThat(anonymous.path("_links").has("create")).isFalse();
        assertThat(anonymous.path("_embedded").path("classes").get(0).path("_links").has("enroll")).isFalse();
        JsonNode all = mapper.readTree(request("GET", "/classes?size=100", null, bruno).body());
        for (JsonNode item : all.path("_embedded").path("classes")) {
            if (item.path("availableSpots").asLong() == 0) {
                assertThat(item.path("_links").has("enroll")).isFalse();
            }
        }
    }

    @Test
    void protectsCapacityAndLetsAdministratorsCancelOrDeleteEnrolledClasses() throws Exception {
        String admin = token("admin@fit.com", "ADMIN");
        String ana = token("ana@fit.com", "USER");
        String bruno = token("bruno@fit.com", "USER");
        String payload = "{\"title\":\"Capacity class\",\"modality\":\"PILATES\",\"level\":\"BEGINNER\",\"instructor\":\"Test Instructor\",\"startsAt\":\"2099-11-03T07:00:00\",\"durationMinutes\":60,\"capacity\":2}";
        long id = mapper.readTree(request("POST", "/classes", payload, admin).body()).path("id").asLong();
        String path = "/classes/" + id;
        try {
            HttpResponse<String> enrolled = request("POST", path + "/enrollments", null, ana);
            assertThat(enrolled.statusCode()).isEqualTo(201);
            assertThat(request("POST", path + "/enrollments", null, bruno).statusCode()).isEqualTo(201);
            assertThat(request("PUT", path, payload.replace("\"capacity\":2", "\"capacity\":1"), admin).statusCode()).isEqualTo(409);
            long enrollmentId = mapper.readTree(enrolled.body()).path("id").asLong();
            assertThat(request("DELETE", "/enrollments/" + enrollmentId, null, admin).statusCode()).isEqualTo(204);
            JsonNode adminEnrollments = mapper.readTree(request("GET", "/enrollments/me", null, admin).body());
            assertThat(adminEnrollments.path("page").path("totalElements").asLong()).isZero();
            assertThat(adminEnrollments.has("_embedded")).isFalse();
        } finally {
            assertThat(request("DELETE", path, null, admin).statusCode()).isEqualTo(204);
        }
        assertThat(get(path).statusCode()).isEqualTo(404);
        for (JsonNode item : mapper.readTree(request("GET", "/enrollments/me?size=100", null, bruno).body()).path("_embedded").path("enrollments")) {
            assertThat(item.path("classId").asLong()).isNotEqualTo(id);
        }
    }

    @Test
    void batchesClassStatisticsAndEnrollmentRelationships() throws Exception {
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            for (int size : new int[]{1, 10}) {
                statistics.clear();
                assertThat(request("GET", "/classes?size=" + size, null, token("ana@fit.com", "USER")).statusCode()).isEqualTo(200);
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
                statistics.clear();
                assertThat(request("GET", "/enrollments/me?size=" + size, null, token("ana@fit.com", "USER")).statusCode()).isEqualTo(200);
                assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
            }
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void serializesCompetingRequestsForTheLastSpot() throws Exception {
        String admin = token("admin@fit.com", "ADMIN");
        String ana = token("ana@fit.com", "USER");
        String bruno = token("bruno@fit.com", "USER");
        String payload = "{\"title\":\"Concurrent class\",\"modality\":\"YOGA\",\"level\":\"BEGINNER\",\"instructor\":\"Test Instructor\",\"startsAt\":\"2099-11-03T07:00:00\",\"durationMinutes\":60,\"capacity\":1}";
        for (int attempt = 0; attempt < 5; attempt++) {
            long id = mapper.readTree(request("POST", "/classes", payload, admin).body()).path("id").asLong();
            String path = "/classes/" + id;
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var start = new java.util.concurrent.CountDownLatch(1);
                var first = executor.submit(() -> {
                    start.await();
                    return request("POST", path + "/enrollments", null, ana).statusCode();
                });
                var second = executor.submit(() -> {
                    start.await();
                    return request("POST", path + "/enrollments", null, bruno).statusCode();
                });
                start.countDown();
                assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
                assertThat(mapper.readTree(get(path).body()).path("availableSpots").asLong()).isZero();
            } finally {
                assertThat(request("DELETE", path, null, admin).statusCode()).isEqualTo(204);
            }
        }
    }

    @Test
    void concurrentCancellationsRecheckEnrollmentAfterAcquiringClassLock() throws Exception {
        String admin = token("admin@fit.com", "ADMIN");
        String ana = token("ana@fit.com", "USER");
        String payload = "{\"title\":\"Concurrent cancellation class\",\"modality\":\"YOGA\",\"level\":\"BEGINNER\",\"instructor\":\"Test Instructor\",\"startsAt\":\"2099-11-03T07:00:00\",\"durationMinutes\":60,\"capacity\":1}";
        HttpResponse<String> created = request("POST", "/classes", payload, admin);
        assertThat(created.statusCode()).isEqualTo(201);
        long classId = mapper.readTree(created.body()).path("id").asLong();
        String path = "/classes/" + classId;
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean statisticsEnabled = statistics.isStatisticsEnabled();
        try {
            HttpResponse<String> enrolled = request("POST", path + "/enrollments", null, ana);
            assertThat(enrolled.statusCode()).isEqualTo(201);
            String cancellation = "/enrollments/" + mapper.readTree(enrolled.body()).path("id").asLong();
            try (var connection = dataSource.getConnection();
                 var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                connection.setAutoCommit(false);
                try {
                    try (var statement = connection.prepareStatement("SELECT id FROM gym_class WHERE id = ? FOR UPDATE")) {
                        statement.setLong(1, classId);
                        try (var result = statement.executeQuery()) {
                            assertThat(result.next()).isTrue();
                        }
                    }
                    statistics.setStatisticsEnabled(true);
                    statistics.clear();
                    var first = executor.submit(() -> request("DELETE", cancellation, null, ana));
                    var second = executor.submit(() -> request("DELETE", cancellation, null, ana));
                    long deadline = System.nanoTime() + java.time.Duration.ofMillis(1500).toNanos();
                    while (statistics.getPrepareStatementCount() < 4 && System.nanoTime() < deadline) {
                        java.util.concurrent.locks.LockSupport.parkNanos(java.time.Duration.ofMillis(1).toNanos());
                    }
                    assertThat(statistics.getPrepareStatementCount()).isGreaterThanOrEqualTo(4);
                    assertThat(first.isDone()).isFalse();
                    assertThat(second.isDone()).isFalse();
                    connection.commit();
                    var responses = java.util.List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS),
                            second.get(5, java.util.concurrent.TimeUnit.SECONDS));
                    assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(204, 404);
                    HttpResponse<String> missing = responses.stream().filter(response -> response.statusCode() == 404)
                            .findFirst().orElseThrow();
                    assertThat(mapper.readTree(missing.body()).path("error").asText()).isEqualTo("Enrollment not found");
                } finally {
                    connection.rollback();
                }
            }
            assertThat(mapper.readTree(get(path).body()).path("availableSpots").asInt()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(statisticsEnabled);
            statistics.clear();
            assertThat(request("DELETE", path, null, admin).statusCode()).isEqualTo(204);
        }
    }

    private void assertJsonHttpError(HttpResponse<String> response, String message) {
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.path("error").asText()).isEqualTo(message);
    }

    private String token(String email, String role) throws Exception {
        com.nimbusds.jwt.SignedJWT jwt = new com.nimbusds.jwt.SignedJWT(
                new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                new com.nimbusds.jwt.JWTClaimsSet.Builder().subject(email).claim("role", role).claim("name", "Test User")
                        .expirationTime(java.util.Date.from(java.time.Instant.now().plusSeconds(3600))).build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(environment.getRequiredProperty("jwt.secret")));
        return jwt.serialize();
    }

    private HttpResponse<String> request(String method, String path, String payload, String token) throws Exception {
        URI uri = URI.create("http://localhost:" + environment.getProperty("local.server.port") + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (payload != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        URI uri = URI.create("http://localhost:" + environment.getProperty("local.server.port") + path);
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
