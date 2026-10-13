package pl.codelobby.execution.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import pl.codelobby.execution.api.SourceFile;
import pl.codelobby.execution.api.TrustedEvaluationRequest.*;

/** A bounded, flat project; no user-supplied archive, build script or flags. */
public record SourceBundle(String source, String additionalFiles, String compilerOptions) {
    public static final int MAX_FILES=20, MAX_SOURCE_BYTES=131072, MAX_BODY_BYTES=2000000,
            MAX_INPUT_BYTES=100000, MAX_OUTPUT_BYTES=65536;
    public static SourceBundle prepare(String source, List<SourceFile> files, Language language, Mode mode) {
        return prepare(files==null?List.of(new SourceFile(SourceFile.entryName(language),source)):files,language,mode);
    }
    public static SourceBundle prepare(List<SourceFile> files, Language language, Mode mode) {
        if (files==null || files.isEmpty() || files.size()>MAX_FILES)
            throw new InvalidSourceFilesException("Podaj 1–20 plików źródłowych");
        Set<String> names=new HashSet<>();long total=0;
        String pattern=switch(language) {
            case CPP -> "[A-Za-z_][A-Za-z0-9_]*\\.(cpp|h|hpp)";
            case JAVA -> "[A-Za-z_][A-Za-z0-9_]*\\.java";
            case PYTHON -> "[A-Za-z_][A-Za-z0-9_]*\\.py";
        };
        for(var file:files) {
            if(file==null || file.path()==null || file.path().length()>120 || !file.path().matches(pattern)
                    || file.content()==null || !StandardCharsets.UTF_8.newEncoder().canEncode(file.content()) || !names.add(file.path().toLowerCase(Locale.ROOT)))
                throw new InvalidSourceFilesException("Niedozwolona nazwa, rozszerzenie lub duplikat pliku");
            total+=file.content().getBytes(StandardCharsets.UTF_8).length;
        }
        if(total>MAX_SOURCE_BYTES) throw new InvalidSourceFilesException("Kod przekracza 131072 bajty UTF-8");
        if(mode==Mode.FUNCTION || mode==Mode.PALINDROME_FUNCTION) {
            if(files.size()!=1) throw new InvalidSourceFilesException("FUNCTION wymaga jednego pliku fragmentu funkcji");
            return new SourceBundle(files.getFirst().content(),null,language==Language.CPP?"-std=c++17 -O2":"");
        }
        String entry=SourceFile.entryName(language);
        var main=files.stream().filter(f->f.path().equals(entry)).findFirst()
                .orElseThrow(()->new InvalidSourceFilesException("Brak pliku wejściowego "+entry));
        try {
            var bytes=new ByteArrayOutputStream();
            try(var zip=new ZipOutputStream(bytes)) {
                for(var f:files.stream().sorted(Comparator.comparing(SourceFile::path)).toList()) {
                    if(f.path().equals(entry))continue;
                    var item=new ZipEntry(f.path());item.setTime(0);zip.putNextEntry(item);
                    zip.write(f.content().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                }
            }
            String options=language==Language.CPP?"-std=c++17 -O2":"";
            return new SourceBundle(main.content(),files.size()>1?Base64.getEncoder().encodeToString(bytes.toByteArray()):null,options.strip());
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot prepare source files",e); }
    }
    public static void validateInputBytes(String input) {
        if(input==null || !StandardCharsets.UTF_8.newEncoder().canEncode(input) || input.getBytes(StandardCharsets.UTF_8).length>MAX_INPUT_BYTES)
            throw new InvalidEvaluationException("Wejście lub expectedOutput przekracza 100000 bajtów UTF-8");
    }
}
