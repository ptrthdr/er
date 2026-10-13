package pl.codelobby.execution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codelobby.piston")
public record PistonProperties(
        String baseUrl,
        String cppVersion,
        int compileTimeoutMs,
        int runTimeoutMs,
        int compileMemoryLimitBytes,
        int runMemoryLimitBytes
) {
}
