package pl.codelobby.execution.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.codelobby.execution.api.SourceFile;
import pl.codelobby.execution.api.TrustedEvaluationRequest.*;
import static org.assertj.core.api.Assertions.*;
class SourceBundleTest {
 @Test void packagesCompanionsAndBuildsServerOwnedCppOptions() throws Exception {
 var bundle=SourceBundle.prepare(null,List.of(new SourceFile("main.cpp","main"),new SourceFile("helper.cpp","helper")),Language.CPP,Mode.FULL_PROGRAM);
 assertThat(bundle.source()).isEqualTo("main");assertThat(bundle.compilerOptions()).isEqualTo("-std=c++17 -O2");
 try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(bundle.additionalFiles())))){
 assertThat(zip.getNextEntry().getName()).isEqualTo("helper.cpp");assertThat(new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("helper");assertThat(zip.getNextEntry()).isNull();}
 }
 @ParameterizedTest @ValueSource(strings={"../main.cpp","/main.cpp","-main.cpp","a;rm.cpp","compile.sh","stdin.txt"})
 void rejectsUnsafeOrReservedNames(String name){assertThatThrownBy(()->SourceBundle.prepare(null,List.of(new SourceFile(name,"x")),Language.CPP,Mode.FULL_PROGRAM)).isInstanceOf(InvalidEvaluationException.class);}
 @Test void rejectsDuplicates(){assertThatThrownBy(()->SourceBundle.prepare(null,List.of(new SourceFile("main.py","x"),new SourceFile("main.py","y")),Language.PYTHON,Mode.FULL_PROGRAM)).isInstanceOf(InvalidEvaluationException.class);}
 @Test void requiresEntryPoint(){assertThatThrownBy(()->SourceBundle.prepare(null,List.of(new SourceFile("Helper.java","x")),Language.JAVA,Mode.FULL_PROGRAM)).isInstanceOf(InvalidEvaluationException.class);}
 @Test void rejectsExcessiveTotalSource(){assertThatThrownBy(()->SourceBundle.prepare(null,List.of(new SourceFile("main.py","x".repeat(131073))),Language.PYTHON,Mode.FULL_PROGRAM)).isInstanceOf(InvalidEvaluationException.class);}
 @Test void functionRejectsMultipleFiles(){assertThatThrownBy(()->SourceBundle.prepare(null,List.of(new SourceFile("main.cpp","x"),new SourceFile("helper.cpp","x")),Language.CPP,Mode.FUNCTION)).isInstanceOf(InvalidEvaluationException.class);}
 @Test void byteLimitUsesUtf8NotCharacterCount() {
 assertThatCode(()->SourceBundle.prepare(null,List.of(new SourceFile("main.py","ą".repeat(65536))),Language.PYTHON,Mode.FULL_PROGRAM)).doesNotThrowAnyException();
 assertThatThrownBy(()->SourceBundle.prepare(null,List.of(new SourceFile("main.py","ą".repeat(65537))),Language.PYTHON,Mode.FULL_PROGRAM)).isInstanceOf(InvalidSourceFilesException.class);
 }
 @Test void rejectsCaseInsensitiveCollisions() {
 assertThatThrownBy(()->SourceBundle.prepare(null,List.of(new SourceFile("main.py","x"),new SourceFile("Main.py","x")),Language.PYTHON,Mode.FULL_PROGRAM)).isInstanceOf(InvalidSourceFilesException.class);
 }
 @Test void fileCountIsBounded() {
 var files=new java.util.ArrayList<SourceFile>();files.add(new SourceFile("main.py","pass"));
 for(int i=0;i<20;i++) files.add(new SourceFile("helper"+i+".py","pass"));
 assertThatThrownBy(()->SourceBundle.prepare(files,Language.PYTHON,Mode.FULL_PROGRAM)).isInstanceOf(InvalidSourceFilesException.class);
 }
 @Test void invalidUnicodeIsNotSilentlyReencoded() {
 assertThatThrownBy(()->SourceBundle.prepare(List.of(new SourceFile("main.py",String.valueOf((char)0xd800))),Language.PYTHON,Mode.FULL_PROGRAM)).isInstanceOf(InvalidSourceFilesException.class);
 }
}
