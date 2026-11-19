package lt.vu.tracker.service;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserExportReaderTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final UserExportReader reader = new UserExportReader(mongo);

    @BeforeEach
    void setUp() {
        when(mongo.stream(any(Query.class), eq(Document.class), anyString()))
                .thenAnswer(call -> Stream.empty());
    }

    @Test
    void projectsIncludeOnlyCurrentUsersMembership() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("projects")))
                .thenReturn(List.of());

        reader.projects("u1");

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Document.class), eq("projects"));

        assertThat(query.getValue().getQueryObject().getString("members.userId"))
                .isEqualTo("u1");

        Document members = (Document)
                query.getValue().getFieldsObject().get("members");
        Document match = (Document) members.get("$elemMatch");
        assertThat(match.getString("userId")).isEqualTo("u1");
    }

    @Test
    void issueSelectionRequiresAccessibleProjectAndUserRelationship() {
        try (var records = reader.issues("u1", List.of("p1"))) {
            assertThat(records.count()).isZero();
        }

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).stream(query.capture(), eq(Document.class), eq("issues"));

        Document criteria = query.getValue().getQueryObject();
        List<Document> conditions = criteria.getList("$and", Document.class);

        assertThat(conditions.getFirst().get("projectId"))
                .isEqualTo(new Document("$in", List.of("p1")));
        assertThat(conditions.get(1).getList("$or", Document.class))
                .containsExactly(
                        new Document("reporterId", "u1"),
                        new Document("assigneeId", "u1")
                );
    }

    @Test
    void commentsAreSelectedByAuthor() {
        try (var records = reader.comments("u1")) {
            assertThat(records.count()).isZero();
        }

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).stream(query.capture(), eq(Document.class), eq("comments"));

        assertThat(query.getValue().getQueryObject().getString("authorId"))
                .isEqualTo("u1");
    }

    @Test
    void historyProjectionOmitsInternalProcessingFlags() {
        try (var records = reader.history("u1")) {
            assertThat(records.count()).isZero();
        }

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).stream(
                query.capture(), eq(Document.class), eq("issue_history")
        );

        assertThat(query.getValue().getQueryObject().getString("actorId"))
                .isEqualTo("u1");
        assertThat(query.getValue().getFieldsObject())
                .doesNotContainKeys("analyticsExported", "searchIndexed", "_class");
    }
}