package pl.codelobby.execution.judge0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record Judge0Response(
        String stdout,
        String stderr,
        @JsonProperty("compile_output") String compile_output,
        String message,
        Double time,
        @JsonProperty("wall_time") Double wall_time,
        Double memory,
        String token,
        @JsonProperty("exit_code") Integer exit_code,
        Status status
) {
    public Judge0Response decoded() {
        return new Judge0Response(decode(stdout), decode(stderr), decode(compile_output), decode(message),
                time, wall_time, memory, token, exit_code, status);
    }

    private static String decode(String value) {
        return value == null ? null : new String(Base64.getMimeDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record Status(Integer id, String description) {
    }
}
