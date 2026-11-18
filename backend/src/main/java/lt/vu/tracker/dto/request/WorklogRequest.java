package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record WorklogRequest(
        @NotNull @Min(1) @Max(1440) Integer minutes,
        Instant startedAt,
        @Size(max = 1000) String note) {}