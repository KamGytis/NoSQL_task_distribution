package lt.vu.tracker.dto.response;

import java.util.List;

public record HistoryPageResponse(List<HistoryEntryResponse> items, int page, int size,
                                  long totalItems, int totalPages) {}