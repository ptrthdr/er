package pl.codelobby.demo;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class DemoErrors {
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> upstream(RestClientResponseException e) {
        return ResponseEntity.status(e.getStatusCode()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(e.getResponseBodyAsString());
    }
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String,String>> unavailable(ResourceAccessException e) {
        return ResponseEntity.status(503).body(Map.of("error","ENGINE_UNAVAILABLE","message","Brak połączenia z modułem; po niepewnym przyjęciu nie ponawiaj automatycznie Submit"));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("error","INVALID_REQUEST"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error","DEMO_REQUEST_REJECTED","message",e.getReason()==null?"Odrzucono żądanie":e.getReason()));
    }
}
