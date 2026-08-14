package pt.eventlab.workflow.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class WorkflowApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Invalid workflow request" : exception.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "invalid_request", "message", message));
    }
}
