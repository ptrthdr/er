package pl.codelobby.execution.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.codelobby.execution.judge0.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
/** Real Spring filter, controller, validation, queue and service; sandbox client mocked. */
@SpringBootTest(properties={"codelobby.internal.api-key=integration-test-key-at-least-32-characters","codelobby.demo.enabled=false","codelobby.execution.engine=judge0"})
@AutoConfigureMockMvc
class InternalApiIntegrationTest {
 static final String KEY="integration-test-key-at-least-32-characters";
 @Autowired MockMvc mvc;
 @Autowired org.springframework.context.ApplicationContext context;
 @Autowired ObjectMapper mapper;
 @MockitoBean Judge0Client judge;
 @org.junit.jupiter.api.BeforeEach void configureCapabilities() {
 when(judge.configuration()).thenReturn(new pl.codelobby.execution.config.Judge0Properties("http://test",54,2,5,128000,64000,20));
 when(judge.runtimeVersions()).thenReturn(java.util.Map.of("JAVA","test-java","PYTHON","test-python","CPP","test-cpp"));
 }
 String payload(String id){return """
 {"submissionId":"%s","language":"CPP","sourceFiles":[{"path":"main.cpp","content":"int main(){}"}],"mode":"FULL_PROGRAM","taskVersionId":"unit-v1",
 "limits":{"cpuTimeMs":1000,"wallTimeMs":3000,"memoryLimitBytes":64000000},
 "testCases":[{"input":"secret","expectedOutput":"5","visibility":"HIDDEN"}]}
 """.formatted(id);}
 @Test void missingAndWrongKeyAreRejected() throws Exception{
 mvc.perform(get("/internal/v1/capabilities")).andExpect(status().isUnauthorized());
 mvc.perform(get("/internal/v1/capabilities").header("X-CodeLobby-Internal-Key","wrong")).andExpect(status().isUnauthorized());}
 @Test void capabilitiesAreAuthenticatedAndNotCached() throws Exception{
 mvc.perform(get("/internal/v1/capabilities").header("X-CodeLobby-Internal-Key",KEY)).andExpect(status().isOk())
 .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.languages.length()").value(3));}
 @Test void demoRoutesAreAbsent() throws Exception{
 mvc.perform(get("/api/tasks/longest-palindrome")).andExpect(status().isNotFound());
 assertThat(context.containsBean("executionController")).isFalse();}
 @Test void malformedAndInvalidRequestsReturn400() throws Exception{
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content("{"))
 .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(payload(""))).andExpect(status().isBadRequest());}
 @Test void unknownAdapterRejectedBeforeEnqueue() throws Exception{
 String body=payload("bad-adapter").replace("\"language\":\"CPP\"","\"language\":\"CPP\",\"mode\":\"FUNCTION\",\"adapterId\":\"unknown\"");
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(body))
 .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));}
 @Test void queuedEvaluationFinishesAndRedactsHiddenData() throws Exception{
 var suite=new Judge0SuiteResponse("codelobby-suite-v1",List.of(new Judge0SuiteResponse.CaseResult("Accepted","5","secret diagnostic",.01,1000.,null)),
 new Judge0SuiteResponse.Benchmark("COMPLETED",2,5,.001,1000.,"SUITE_PROCESS_CPU","SANDBOX",null));
 when(judge.executeSuiteFiles(any(),anyList(),any(),any())).thenReturn(new Judge0Response("CODELOBBY_TEST_SUITE_RESULT_V1:"+mapper.writeValueAsString(suite),null,null,null,null,null,null,null,0,new Judge0Response.Status(3,"Accepted")));
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(payload("integration-accepted"))).andExpect(status().isAccepted());
 long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);String body;
 do{body=mvc.perform(get("/internal/v1/evaluation-jobs/integration-accepted").header("X-CodeLobby-Internal-Key",KEY)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
 if(mapper.readTree(body).path("state").asText().equals("FINISHED"))break;Thread.sleep(5);
 }while(System.nanoTime()<deadline);
 var json=mapper.readTree(body);assertThat(json.path("state").asText()).isEqualTo("FINISHED");assertThat(json.at("/result/status").asText()).isEqualTo("ACCEPTED");
 assertThat(json.at("/result/testResults/0/output").isNull()).isTrue();assertThat(body).doesNotContain("secret diagnostic");
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(payload("integration-accepted").replace("int main(){}","changed")))
 .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUBMISSION_ID_CONFLICT"));}
 @Test void missingJobReturnsStableError() throws Exception{
 mvc.perform(get("/internal/v1/evaluation-jobs/missing").header("X-CodeLobby-Internal-Key",KEY)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));}
 @Test void versionWithoutTaskIdIsRejected() throws Exception {
 String body=payload("bad-reference").replace("\"taskVersionId\":\"unit-v1\",", "");
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(body)).andExpect(status().isBadRequest());
 }
 @Test void unknownFieldsAreRejected() throws Exception {
 String body=payload("unknown-field").replace("\"language\":\"CPP\"", "\"language\":\"CPP\",\"compilerOptions\":\"unsafe\"");
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(body)).andExpect(status().isBadRequest());
 }
 @Test void judgeUnavailableOrTimeoutReturns503() throws Exception {
 when(judge.executeFiles(any(),anyString(),any(),any())).thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));
 String body="{\"requestId\":\"run\",\"language\":\"CPP\",\"sourceFiles\":[{\"path\":\"main.cpp\",\"content\":\"int main(){}\"}],\"mode\":\"FULL_PROGRAM\",\"taskVersionId\":\"unit-v1\",\"stdin\":\"\",\"limits\":{\"cpuTimeMs\":1000,\"wallTimeMs\":3000,\"memoryLimitBytes\":64000000}}";
 mvc.perform(post("/internal/v1/runs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(body)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EXECUTION_UNAVAILABLE"));
 }
 @Test void retiredSourceCodeAndEntryPointFieldsAreRejected() throws Exception {
 String old="{\"requestId\":\"r\",\"sourceCode\":\"code\"}";
 mvc.perform(post("/internal/v1/runs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(old)).andExpect(status().isBadRequest());
 String body=payload("entry").replace("\"language\":\"CPP\"","\"language\":\"CPP\",\"entryPoint\":\"main\"");
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(body)).andExpect(status().isBadRequest());
 }
 @Test void completedReplayReturns200() throws Exception {
 when(judge.executeSuiteFiles(any(),anyList(),any(),any())).thenReturn(new Judge0Response(null,null,"syntax error",null,null,null,null,null,null,new Judge0Response.Status(6,"Compilation Error")));
 String body=payload("replay-finished");
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(body)).andExpect(status().isAccepted());
 long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
 while(!mapper.readTree(mvc.perform(get("/internal/v1/evaluation-jobs/replay-finished").header("X-CodeLobby-Internal-Key",KEY)).andReturn().getResponse().getContentAsString()).path("state").asText().equals("FINISHED")&&System.nanoTime()<end)Thread.sleep(5);
 mvc.perform(post("/internal/v1/evaluation-jobs").header("X-CodeLobby-Internal-Key",KEY).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("COMPILATION_ERROR"));
 }
}
