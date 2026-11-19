package lt.vu.tracker.dto.response;

public record EventCountResponse(
        String eventType,
        long count
) {}