package lt.vu.tracker.service;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static lt.vu.tracker.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserDataExportServiceTest {
    private final UserExportReader reader = mock(UserExportReader.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final UserDataExportService service =
            new UserDataExportService(reader, json);

    @BeforeEach
    void setUp() {
        when(reader.projects("u1")).thenReturn(List.of());
        when(reader.issues(eq("u1"), anyList()))
                .thenAnswer(call -> Stream.empty());
        when(reader.comments("u1")).thenAnswer(call -> Stream.empty());
        when(reader.worklogs("u1")).thenAnswer(call -> Stream.empty());
        when(reader.filters("u1")).thenAnswer(call -> Stream.empty());
        when(reader.notifications("u1")).thenAnswer(call -> Stream.empty());
        when(reader.invitations("u1")).thenAnswer(call -> Stream.empty());
        when(reader.history("u1")).thenAnswer(call -> Stream.empty());
    }

    @Test
    void buildsAllEntriesAndExcludesPasswordHash() throws Exception {
        var current = user("u1", "owner@test.lt");
        current.setPasswordHash("SECRET_HASH");

        Map<String, String> entries = unzip(service.build(current));

        assertThat(entries.keySet()).containsExactlyInAnyOrder(
                "profile.json", "projects.json", "issues.json",
                "comments.json", "worklogs.json", "saved-filters.json",
                "notifications.json", "invitations.json",
                "issue-history.json", "manifest.json"
        );

        var profile = json.readTree(entries.get("profile.json"));
        assertThat(profile.get("email").asText()).isEqualTo("owner@test.lt");
        assertThat(profile.has("passwordHash")).isFalse();
        assertThat(entries.values())
                .noneMatch(value -> value.contains("SECRET_HASH"));

        for (String name : List.of(
                "projects.json", "issues.json", "comments.json",
                "worklogs.json", "saved-filters.json", "notifications.json",
                "invitations.json", "issue-history.json"
        )) {
            assertThat(entries.get(name)).isEqualTo("[]");
        }
    }

    @Test
    void closesDocumentStreamsAndRecordsTheirCounts() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        when(reader.comments("u1")).thenAnswer(call ->
                Stream.of(new Document("_id", "c1")
                                .append("text", "Lietuviškas komentaras"))
                        .onClose(() -> closed.set(true))
        );

        var entries = unzip(service.build(user("u1", "owner@test.lt")));

        assertThat(closed.get()).isTrue();
        assertThat(entries.get("comments.json"))
                .contains("Lietuviškas komentaras");

        var manifest = json.readTree(entries.get("manifest.json"));
        assertThat(manifest.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(manifest.get("recordCounts").get("comments.json").asLong())
                .isEqualTo(1L);
    }

    private Map<String, String> unzip(byte[] archive) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8
        )) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(),
                        new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}