package lt.vu.tracker.dto.response;

import java.time.LocalDate;

public record DailyActivityResponse(
        LocalDate day,
        String eventType,
        long count
) {}