package dev.orbit.worker;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutor {
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private static final int MAX_SUM_VALUES = 10_000;
    private static final int MAX_NUMERIC_CHARS = 256;
    private static final int MAX_NUMERIC_EXPONENT = 1_000;
    private final ObjectMapper mapper;

    public TaskExecutor(ObjectMapper mapper) { this.mapper = mapper; }

    public String execute(String taskType, String payloadJson) throws Exception {
        if (payloadJson == null || payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Task payload must not exceed 1 MiB");
        }
        Map<String, Object> payload = mapper.readValue(payloadJson, new TypeReference<>() { });
        Map<String, Object> result = switch (taskType.toLowerCase()) {
            case "echo" -> Map.of("message", String.valueOf(payload.getOrDefault("message", "")));
            case "sleep" -> executeSleep(payload);
            case "sum" -> executeSum(payload);
            default -> throw new IllegalArgumentException("Unsupported task type: " + taskType);
        };
        return mapper.writeValueAsString(result);
    }

    private Map<String, Object> executeSleep(Map<String, Object> payload) throws InterruptedException {
        long millis = integerNumber(payload.getOrDefault("millis", 1000L));
        if (millis < 0 || millis > 30_000) {
            throw new IllegalArgumentException("sleep.millis must be between 0 and 30000");
        }
        Thread.sleep(millis);
        return Map.of("sleptMillis", millis);
    }

    private Map<String, Object> executeSum(Map<String, Object> payload) {
        Object raw = payload.get("values");
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("sum requires a non-empty 'values' array");
        }
        if (values.size() > MAX_SUM_VALUES) {
            throw new IllegalArgumentException("sum accepts at most " + MAX_SUM_VALUES + " values");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Object value : values) sum = sum.add(decimalNumber(value));
        BigDecimal result = sum.stripTrailingZeros();
        // stripTrailingZeros() turns 60 into 6E+1.
        if (result.scale() < 0) result = result.setScale(0);
        return Map.of("result", result);
    }

    private long integerNumber(Object value) {
        try {
            return parseNumber(value).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Expected an integer value within the 64-bit range", exception);
        }
    }

    private BigDecimal decimalNumber(Object value) {
        try {
            return parseNumber(value);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Expected a finite decimal number", exception);
        }
    }

    // BigDecimal.add aligns scales, so an exponent like 1E100000000 would stall the worker thread.
    private BigDecimal parseNumber(Object value) {
        BigDecimal number = new BigDecimal(boundedNumericString(value));
        if (Math.abs((long) number.scale()) > MAX_NUMERIC_EXPONENT) throw new ArithmeticException("Numeric exponent is too large");
        return number;
    }

    private String boundedNumericString(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) {
            throw new IllegalArgumentException("Expected a numeric value");
        }
        String text = String.valueOf(value);
        if (text.length() > MAX_NUMERIC_CHARS) throw new IllegalArgumentException("Numeric value is too large");
        return text;
    }
}
