package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.UserResponse;
import lt.vu.tracker.model.User;
import org.bson.Document;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class UserDataExportService {
    private final UserExportReader reader;
    private final JsonMapper json;

    public byte[] build(User current) throws IOException {
        String userId = current.getId();
        Instant exportedAt = Instant.now();

        var projects = reader.projects(userId);
        var projectIds = projects.stream()
                .map(project -> project.get("_id").toString())
                .toList();

        Map<String, Long> counts = new LinkedHashMap<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(
                bytes, StandardCharsets.UTF_8
        )) {
            writeJson(zip, "profile.json", UserResponse.from(current));
            counts.put("profile.json", 1L);

            counts.put("projects.json",
                    writeDocuments(zip, "projects.json", projects.stream()));

            counts.put("issues.json",
                    writeDocuments(zip, "issues.json",
                            reader.issues(userId, projectIds)));

            counts.put("comments.json",
                    writeDocuments(zip, "comments.json", reader.comments(userId)));

            counts.put("worklogs.json",
                    writeDocuments(zip, "worklogs.json", reader.worklogs(userId)));

            counts.put("saved-filters.json",
                    writeDocuments(zip, "saved-filters.json", reader.filters(userId)));

            counts.put("notifications.json",
                    writeDocuments(zip, "notifications.json",
                            reader.notifications(userId)));

            counts.put("invitations.json",
                    writeDocuments(zip, "invitations.json",
                            reader.invitations(userId)));

            counts.put("issue-history.json",
                    writeDocuments(zip, "issue-history.json",
                            reader.history(userId)));

            writeJson(zip, "manifest.json", Map.of(
                    "formatVersion", 1,
                    "exportedAt", exportedAt.toString(),
                    "userId", userId,
                    "recordCounts", counts
            ));
        }

        return bytes.toByteArray();
    }

    private void writeJson(
            ZipOutputStream zip,
            String name,
            Object value
    ) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(json.writeValueAsBytes(value));
        zip.closeEntry();
    }

    private long writeDocuments(
            ZipOutputStream zip,
            String name,
            Stream<Document> records
    ) throws IOException {
        try (records) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write('[');

            Iterator<Document> iterator = records.iterator();
            long count = 0;

            while (iterator.hasNext()) {
                if (count > 0) {
                    zip.write(',');
                }

                String document = iterator.next().toJson();
                zip.write(document.getBytes(StandardCharsets.UTF_8));
                count++;
            }

            zip.write(']');
            zip.closeEntry();
            return count;
        }
    }
}