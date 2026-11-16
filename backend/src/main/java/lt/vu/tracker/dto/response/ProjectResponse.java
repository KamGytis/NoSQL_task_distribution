package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.enums.ProjectRole;

import java.time.Instant;
import java.util.List;

public record ProjectResponse(String id, String key, String name, String description,
                              String leadId, List<MemberResponse> members,
                              ProjectRole myRole, Instant createdAt) {}