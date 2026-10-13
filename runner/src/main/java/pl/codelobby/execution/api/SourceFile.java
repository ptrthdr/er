package pl.codelobby.execution.api;
import jakarta.validation.constraints.*;
public record SourceFile(@NotBlank @Size(max=120) String path,
                         @NotNull @Size(max=131072) String content) {
    public static String entryName(TrustedEvaluationRequest.Language language) {
        return switch(language) {case CPP -> "main.cpp";case PYTHON -> "main.py";case JAVA -> "Main.java";};
    }
}
