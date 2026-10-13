package pl.codelobby.execution.piston;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record PistonResponse(
        Stage compile,
        Stage run,
        String language,
        String version
) {
    public record Stage(
            String stdout,
            String stderr,
            Integer code,
            String signal,
            String message,
            String status,
            Long cpu_time,
            Long wall_time,
            Long memory
    ) {
    }
}
