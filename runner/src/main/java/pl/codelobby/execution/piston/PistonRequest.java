package pl.codelobby.execution.piston;

import java.util.List;

public record PistonRequest(
        String language,
        String version,
        List<SourceFile> files,
        String stdin,
        int compile_timeout,
        int run_timeout,
        int compile_cpu_time,
        int run_cpu_time,
        int compile_memory_limit,
        int run_memory_limit
) {
    public record SourceFile(String name, String content) {
    }
}
