package pl.codelobby.execution.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.codelobby.execution.api.TrustedEvaluationRequest.Mode;
import static org.assertj.core.api.Assertions.*;
class CppAdaptersTest {
 @Test void fullProgramDoesNotRequireAdapter(){assertThat(CppAdapters.resolve(Mode.FULL_PROGRAM,null)).isNull();}
 @Test void rejectsUnknownAdapter(){assertThatThrownBy(()->CppAdapters.resolve(Mode.FUNCTION,"unknown")).isInstanceOf(InvalidEvaluationException.class);}
 @ParameterizedTest @ValueSource(strings={"ABC","a\nb","123","ą","abc\r\n"})
 void rejectsWrongInputFormat(String input){assertThatThrownBy(()->CppAdapters.resolve(Mode.FUNCTION,"vowel-count-v1").validateInput(input)).isInstanceOf(InvalidEvaluationException.class);}
 @ParameterizedTest @ValueSource(strings={"","\n","abc","abc\n"})
 void acceptsInput(String input){assertThatCode(()->CppAdapters.resolve(Mode.FUNCTION,"vowel-count-v1").validateInput(input)).doesNotThrowAnyException();}
}
