package lt.vu.tracker.dto.response;

import java.util.List;

public record WorklogListResponse(long totalMinutes, String totalTime, List<WorklogResponse> entries) {}