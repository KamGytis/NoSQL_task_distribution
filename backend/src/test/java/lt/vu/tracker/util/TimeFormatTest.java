package lt.vu.tracker.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeFormatTest {
    @Test
    void zeroAndNegativeShowAsZero() {
        assertThat(TimeFormat.minutes(0)).isEqualTo("0m");
        assertThat(TimeFormat.minutes(-5)).isEqualTo("0m");
    }

    @Test
    void minutesOnly() {
        assertThat(TimeFormat.minutes(1)).isEqualTo("1m");
        assertThat(TimeFormat.minutes(45)).isEqualTo("45m");
    }

    @Test
    void wholeHoursDropTheMinutes() {
        assertThat(TimeFormat.minutes(60)).isEqualTo("1h");
        assertThat(TimeFormat.minutes(1440)).isEqualTo("24h");
    }

    @Test
    void hoursAndMinutes() {
        assertThat(TimeFormat.minutes(90)).isEqualTo("1h 30m");
        assertThat(TimeFormat.minutes(1501)).isEqualTo("25h 1m");
    }
}