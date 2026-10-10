package pl.codelobby.execution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codelobby.judge0")
public record Judge0Properties(
        String baseUrl,
        int languageId,
        double cpuTimeLimitSeconds,
        double wallTimeLimitSeconds,
        int memoryLimitKilobytes,
        int stackLimitKilobytes,
        int maxProcessesAndThreads
) {
}
