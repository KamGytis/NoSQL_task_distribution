package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.SavedFilterRequest;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.dto.response.SavedFilterResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.SavedFilter;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.SavedFilterRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SavedFilterService {
    private final SavedFilterRepository filters;
    private final PermissionService permissions;
    private final MongoTemplate mongo;

    public SavedFilterResponse create(SavedFilterRequest request, User current) {
        permissions.requireRole(
                request.projectId(), current.getId(), ProjectRole.VIEWER
        );

        SavedFilter filter = new SavedFilter();
        filter.setOwnerId(current.getId());
        filter.setCreatedAt(Instant.now());
        applyRequest(filter, request);

        return SavedFilterResponse.from(filters.insert(filter));
    }

    public List<SavedFilterResponse> list(User current) {
        return filters.findByOwnerIdOrderByCreatedAtDescIdDesc(current.getId())
                .stream()
                .map(SavedFilterResponse::from)
                .toList();
    }

    public SavedFilterResponse get(String id, User current) {
        return SavedFilterResponse.from(requireOwned(id, current));
    }

    public SavedFilterResponse update(
            String id,
            SavedFilterRequest request,
            User current
    ) {
        SavedFilter filter = requireOwned(id, current);

        permissions.requireRole(
                request.projectId(), current.getId(), ProjectRole.VIEWER
        );
        applyRequest(filter, request);

        return SavedFilterResponse.from(filters.save(filter));
    }

    public void delete(String id, User current) {
        filters.delete(requireOwned(id, current));
    }

    public List<IssueResponse> execute(
            String id,
            int page,
            int size,
            User current
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100"
            );
        }

        SavedFilter filter = requireOwned(id, current);
        permissions.requireRole(
                filter.getProjectId(), current.getId(), ProjectRole.VIEWER
        );

        Criteria criteria = Criteria.where("projectId").is(filter.getProjectId());

        if (filter.getStatus() != null) {
            criteria.and("status").is(filter.getStatus());
        }
        if (filter.getAssigneeId() != null) {
            criteria.and("assigneeId").is(filter.getAssigneeId());
        }
        if (filter.getTitleContains() != null) {
            criteria.and("title").regex(
                    Pattern.quote(filter.getTitleContains()), "i"
            );
        }

        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                .skip((long) page * size)
                .limit(size);

        return mongo.find(query, Issue.class)
                .stream()
                .map(IssueResponse::from)
                .toList();
    }

    private SavedFilter requireOwned(String id, User current) {
        return filters.findByIdAndOwnerId(id, current.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Saved filter not found"
                ));
    }

    private void applyRequest(SavedFilter filter, SavedFilterRequest request) {
        filter.setName(request.name().trim());
        filter.setProjectId(request.projectId());
        filter.setStatus(request.status());
        filter.setAssigneeId(request.assigneeId());

        String title = request.titleContains();
        if (title != null) {
            title = title.trim();
            if (title.isEmpty()) {
                title = null;
            }
        }

        filter.setTitleContains(title);
        filter.setUpdatedAt(Instant.now());
    }
}