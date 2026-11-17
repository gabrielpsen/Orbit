package dev.orbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import dev.orbit.worker.TaskExecutor;
import org.junit.jupiter.api.Test;

class TaskExecutorTest {
    private final TaskExecutor executor = new TaskExecutor(new ObjectMapper());

    @Test
    void sumsValues() throws Exception {
        assertThat(executor.execute("sum", "{\"values\":[10,20,30]}")).contains("60");
    }

    @Test
    void sumsDecimalValuesWithoutTruncatingThem() throws Exception {
        assertThat(executor.execute("sum", "{\"values\":[1.25,2.5]}"))
                .contains("3.75");
    }

    @Test
    void sumsCommonFloatingPointDecimalsExactly() throws Exception {
        assertThat(executor.execute("sum", "{\"values\":[0.1,0.2]}")).contains("0.3");
    }

    @Test
    void rejectsExcessiveSumArrays() {
        String values = "[" + "1,".repeat(10_000) + "1]";
        assertThatThrownBy(() -> executor.execute("sum", "{\"values\":" + values + "}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void rejectsHugeExponentsThatWouldStallTheWorker() {
        assertThatThrownBy(() -> executor.execute("sum", "{\"values\":[\"1E100000000\",1]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite decimal");
    }

    @Test
    void rejectsUnknownTaskType() {
        assertThatThrownBy(() -> executor.execute("shell", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported task type");
    }

    @Test
    void rejectsLongSleepRequests() {
        assertThatThrownBy(() -> executor.execute("sleep", "{\"millis\":999999}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 30000");
    }
}
