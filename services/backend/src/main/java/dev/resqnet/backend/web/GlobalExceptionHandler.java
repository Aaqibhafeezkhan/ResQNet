package dev.resqnet.backend.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Uniform error responses.
 *
 * <p>Error bodies carry a stable code and a human-readable message, and never a
 * stack trace or an internal exception name. A responder client that shows the
 * raw failure of a validation error learns nothing useful and a great deal about
 * the internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(dev.resqnet.backend.service.IncidentService
            .IncidentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(dev.resqnet.backend.service
            .IncidentService.IncidentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(dev.resqnet.backend.service.IncidentService.ForbiddenActionException.class)
    public ResponseEntity<Map<String, Object>> forbidden(dev.resqnet.backend.service
            .IncidentService.ForbiddenActionException e) {
        log.warn("Refused an out-of-role action: {}", e.getMessage());
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
    public ResponseEntity<Map<String, Object>> accessDenied(RuntimeException e) {
        log.warn("Refused an out-of-role action: {}", e.getMessage());
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "This account is not permitted to perform that action");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
        return problem(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        // A body that does not parse is the caller's mistake, not a server
        // fault. Reporting it as 500 would point an operator at the wrong thing.
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_BODY",
                "The request body could not be read as JSON");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        Map<String, Object> body = base("VALIDATION_FAILED", "The request was not valid");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * A wrong Content-Type is a client error, not a server fault.
     *
     * <p>Spring raises this before the controller runs, and the catch-all
     * handler was turning it into a 500. A device posting
     * {@code application/x-www-form-urlencoded} to a JSON endpoint is a bug in
     * the device, and answering 500 tells its logs that the gateway is broken
     * when it is not. It also logged a full stack trace for what is ordinary
     * client misbehaviour.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> unsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(base("UNSUPPORTED_MEDIA_TYPE",
                        "This endpoint accepts application/json"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong handling that request");
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String code,
                                                               String message) {
        return ResponseEntity.status(status).body(base(code, message));
    }

    private static Map<String, Object> base(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("at", Instant.now().toString());
        return body;
    }
}
