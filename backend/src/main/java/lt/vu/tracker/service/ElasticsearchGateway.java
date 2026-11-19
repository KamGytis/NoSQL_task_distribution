package lt.vu.tracker.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ElasticsearchGateway {
    private final HttpClient http;
    private final JsonMapper json;
    private final String base;
    private final String index;

    public ElasticsearchGateway(
            @Qualifier("searchHttpClient") HttpClient http,
            JsonMapper json,
            @Value("${search.url}") String base,
            @Value("${search.index}") String index
    ) {
        if (!index.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid search index name");
        }
        this.http = http;
        this.json = json;
        this.base = base.replaceAll("/+$", "");
        this.index = index;
    }

    public void index(String id, long version, Map<String, Object> document) {
        String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
        String path = "/_doc/" + encodedId
                + "?version_type=external_gte&version=" + version;

        request("PUT", path, document, true);
    }

    public List<String> search(Map<String, Object> body) {
        String response = request("POST", "/_search", body, false);

        try {
            var root = json.readTree(response);

            if (root.path("timed_out").asBoolean()
                    || root.path("_shards").path("failed").asInt() > 0
                    || !root.path("hits").path("hits").isArray()) {
                throw unavailable();
            }

            List<String> ids = new ArrayList<>();
            for (var hit : root.path("hits").path("hits")) {
                ids.add(hit.path("_id").asText());
            }
            return ids;
        } catch (JacksonException exception) {
            throw unavailable();
        }
    }

    public void refresh() {
        request("POST", "/_refresh", null, false);
    }

    private String request(
            String method,
            String path,
            Map<String, Object> body,
            boolean allowVersionConflict
    ) {
        try {
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
            if (body != null) {
                publisher = HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(body)
                );
            }

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(base + "/" + index + path)
                    )
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .method(method, publisher)
                    .build();

            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            if (allowVersionConflict
                    && response.statusCode() == 409
                    && response.body().contains("version_conflict_engine_exception")) {
                return response.body();
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | JacksonException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Search is temporarily unavailable"
        );
    }
}