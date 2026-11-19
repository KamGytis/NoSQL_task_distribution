package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UserExportReader {
    private final MongoTemplate mongo;

    public List<Document> projects(String userId) {
        Query query = select(
                Criteria.where("members.userId").is(userId),
                "key", "name", "description", "leadId", "createdAt"
        );
        query.fields().elemMatch(
                "members", Criteria.where("userId").is(userId)
        );

        return mongo.find(query, Document.class, "projects");
    }

    public Stream<Document> issues(String userId, List<String> projectIds) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("projectId").in(projectIds),
                new Criteria().orOperator(
                        Criteria.where("reporterId").is(userId),
                        Criteria.where("assigneeId").is(userId)
                )
        );

        return stream("issues", criteria,
                "key", "projectId", "sprintId", "title", "description",
                "status", "reporterId", "assigneeId", "createdAt");
    }

    public Stream<Document> comments(String userId) {
        return stream("comments", Criteria.where("authorId").is(userId),
                "issueId", "issueKey", "projectId", "authorId",
                "text", "mentions", "createdAt", "editedAt");
    }

    public Stream<Document> worklogs(String userId) {
        return stream("worklogs", Criteria.where("userId").is(userId),
                "issueId", "issueKey", "projectId", "userId",
                "minutes", "startedAt", "note", "createdAt", "updatedAt");
    }

    public Stream<Document> filters(String userId) {
        return stream("saved_filters", Criteria.where("ownerId").is(userId),
                "ownerId", "projectId", "name", "status", "assigneeId",
                "titleContains", "createdAt", "updatedAt");
    }

    public Stream<Document> notifications(String userId) {
        return stream("notifications", Criteria.where("userId").is(userId),
                "userId", "type", "actorId", "projectId",
                "issueKey", "commentId", "read", "createdAt");
    }

    public Stream<Document> invitations(String userId) {
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("invitedUserId").is(userId),
                Criteria.where("invitedBy").is(userId)
        );

        return stream("invitations", criteria,
                "projectId", "projectName", "invitedUserId",
                "invitedBy", "role", "status", "createdAt");
    }

    public Stream<Document> history(String userId) {
        return stream("issue_history", Criteria.where("actorId").is(userId),
                "issueId", "issueKey", "projectId", "actorId",
                "action", "changes", "createdAt");
    }

    private Stream<Document> stream(
            String collection,
            Criteria criteria,
            String... fields
    ) {
        return mongo.stream(select(criteria, fields), Document.class, collection);
    }

    private Query select(Criteria criteria, String... fields) {
        Query query = Query.query(criteria);
        query.fields().include(fields);
        return query;
    }
}