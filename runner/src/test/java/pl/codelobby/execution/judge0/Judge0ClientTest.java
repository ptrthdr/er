package pl.codelobby.execution.judge0;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;
import pl.codelobby.execution.config.Judge0Properties;
import pl.codelobby.execution.api.TrustedEvaluationRequest.Limits;
import pl.codelobby.execution.service.InvalidEvaluationException;
import static org.assertj.core.api.Assertions.*;
class Judge0ClientTest {
 final Judge0Client client=new Judge0Client(RestClient.builder(),new Judge0Properties("http://127.0.0.1:1",54,2,5,128000,64000,20),new ObjectMapper());
 @Test void acceptsBoundaryLimits(){assertThatCode(()->client.validateLimits(new Limits(2000,5000,128000000))).doesNotThrowAnyException();}
 @ParameterizedTest @CsvSource({"0,3000,1000","2001,5000,1000","1000,999,1000","1000,5001,1000","1000,3000,999","1000,3000,128000001"})
 void rejectsInvalidLimits(long cpu,long wall,long memory){assertThatThrownBy(()->client.validateLimits(new Limits(cpu,wall,memory))).isInstanceOf(InvalidEvaluationException.class);}
 @Test void rejectsMissingLimits(){assertThatThrownBy(()->client.validateLimits(null)).isInstanceOf(InvalidEvaluationException.class);}
 @Test void decodesUtf8AndPreservesNulls(){
 String encoded=java.util.Base64.getEncoder().encodeToString("Zażółć\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
 var result=new Judge0Response(encoded,null,encoded,null,.01,.02,1000.,"token",0,new Judge0Response.Status(3,"Accepted")).decoded();
 assertThat(result.stdout()).isEqualTo("Zażółć\n");assertThat(result.stderr()).isNull();assertThat(result.time()).isEqualTo(.01);
 }
}
