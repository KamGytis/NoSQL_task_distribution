package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.dto.response.IssueSuggestionResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.IssueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final PermissionService permissions;
    private final ElasticsearchGateway elasticsearch;
    private final IssueRepository issues;

    public List<IssueResponse> search(
            String projectId,
            String q,
            int page,
            int size,
            User current
    ) {
        permissions.requireRole(projectId, current.getId(), ProjectRole.VIEWER);
        String text = validateText(q);

        if (page < 0 || size < 1 || size > 100
                || (long) page * size + size > 10000) {
            throw badRequest("Invalid page or size. Search supports the first 10000 hits");
        }

        Map<String, Object> textQuery = Map.of(
                "multi_match", Map.of(
                        "query", text,
                        "fields", List.of("key^4", "title^3", "description"),
                        "operator", "and"
                )
        );

        List<String> ids = elasticsearch.search(
                body(projectId, textQuery, page * size, size)
        );
        return loadCurrentIssues(projectId, ids);
    }

    public List<IssueSuggestionResponse> suggest(
            String projectId,
            String q,
            int size,
            User current
    ) {
        permissions.requireRole(projectId, current.getId(), ProjectRole.VIEWER);
        String text = validateText(q);

        if (size < 1 || size > 20) {
            throw badRequest("Suggestion size must be between 1 and 20");
        }

        Map<String, Object> textQuery = Map.of(
                "bool", Map.of(
                        "should", List.of(
                                Map.of("multi_match", Map.of(
                                        "query", text,
                                        "type", "bool_prefix",
                                        "fields", List.of(
                                                "title",
                                                "title._2gram",
                                                "title._3gram"
                                        )
                                )),
                                Map.of("prefix", Map.of(
                                        "key.exact", text.toLowerCase(Locale.ROOT)
                                ))
                        ),
                        "minimum_should_match", 1
                )
        );

        List<String> ids = elasticsearch.search(
                body(projectId, textQuery, 0, size)
        );

        return loadCurrentIssues(projectId, ids).stream()
                .map(issue -> new IssueSuggestionResponse(
                        issue.key(), issue.title()
                ))
                .toList();
    }

    private Map<String, Object> body(
            String projectId,
            Map<String, Object> textQuery,
            int from,
            int size
    ) {
        return Map.of(
                "from", from,
                "size", size,
                "_source", false,
                "sort", List.of(
                        Map.of("_score", "desc"),
                        Map.of("key.exact", "asc")
                ),
                "query", Map.of(
                        "bool", Map.of(
                                "filter", List.of(
                                        Map.of("term", Map.of("projectId", projectId)),
                                        Map.of("term", Map.of("deleted", false))
                                ),
                                "must", List.of(textQuery)
                        )
                )
        );
    }

    private List<IssueResponse> loadCurrentIssues(
            String projectId,
            List<String> ids
    ) {
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<String, Issue> current = issues.findAllById(ids).stream()
                .filter(issue -> projectId.equals(issue.getProjectId()))
                .collect(Collectors.toMap(Issue::getId, Function.identity()));

        return ids.stream()
                .map(current::get)
                .filter(Objects::nonNull)
                .map(IssueResponse::from)
                .toList();
    }

    private String validateText(String q) {
        if (q == null) {
            throw badRequest("Query is required");
        }
        String text = q.strip();
        if (text.length() < 2 || text.length() > 200) {
            throw badRequest("Query must contain between 2 and 200 characters");
        }
        return text;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}