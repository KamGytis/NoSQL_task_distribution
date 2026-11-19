package lt.vu.tracker.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static lt.vu.tracker.TestData.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ElasticsearchGatewayTest {
    private final HttpClient http = mock(HttpClient.class);
    private final ElasticsearchGateway gateway = new ElasticsearchGateway(
            http, JsonMapper.builder().build(), "http://localhost:9200", "issues-test"
    );

    @SuppressWarnings("unchecked")
    private final HttpResponse<String> response = mock(HttpResponse.class);

    @Test
    void readsIdsFromSearchResponse() throws Exception {
        reply(200, """
                {
                  "timed_out":false,
                  "_shards":{"failed":0},
                  "hits":{"hits":[{"_id":"i1"},{"_id":"i2"}]}
                }
                """);

        assertThat(gateway.search(Map.of("size", 2)))
                .isEqualTo(List.of("i1", "i2"));
    }

    @Test
    void staleDocumentVersionIsAcceptedAsAlreadySuperseded() throws Exception {
        reply(409, "{\"error\":{\"type\":\"version_conflict_engine_exception\"}}");

        gateway.index("i1", 3, Map.of("deleted", false));

        var captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captured.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captured.getValue().uri().toString())
                .contains("version_type=external_gte", "version=3");
    }

    @Test
    void serverFailureReturns503() throws Exception {
        reply(500, "{\"error\":\"unavailable\"}");

        assertStatus(() -> gateway.search(Map.of()), 503);
    }

    private void reply(int status, String body) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }
}