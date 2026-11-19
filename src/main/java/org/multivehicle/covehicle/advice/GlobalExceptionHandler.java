package org.multivehicle.covehicle.advice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.multivehicle.covehicle.exception.badrequest.BadRequestException;
import org.multivehicle.covehicle.exception.conflict.ConflictException;
import org.multivehicle.covehicle.exception.forbidden.ForbiddenException;
import org.multivehicle.covehicle.exception.internalservererror.InternalServerErrorException;
import org.multivehicle.covehicle.exception.notfound.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final String TYPE_PREFIX = "urn:covehicle:error:";

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        log.warn("Validation failed on {}: {}", getWebRequestPath(request), fieldErrors);
        ProblemDetail pd = this.problem(HttpStatus.BAD_REQUEST, "Validation failed", getWebRequestPath(request));
        pd.setType(URI.create(TYPE_PREFIX + "validation-error"));
        pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, @Nullable Object body, @NonNull HttpHeaders headers, @NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            if (statusCode.is4xxClientError()) log.warn("{} on {}: {}", statusCode, getWebRequestPath(request), pd.getDetail());
            if (pd.getProperties() == null || !pd.getProperties().containsKey("timestamp")) pd.setProperty("timestamp", Instant.now());
            if (pd.getInstance() == null) pd.setInstance(URI.create(getWebRequestPath(request)));
            if (pd.getType().toString().equals("about:blank")) pd.setType(URI.create(TYPE_PREFIX + slug(statusCode)));
        }
        return response;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException e, HttpServletRequest req) {
        log.warn("Not found on {}: {}", req.getRequestURI(), e.getMessage());
        return this.respond(HttpStatus.NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException e, HttpServletRequest req) {
        log.warn("Forbidden on {}: {}", req.getRequestURI(), e.getMessage());
        return this.respond(HttpStatus.FORBIDDEN, e.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException e, HttpServletRequest req) {
        log.warn("Business rule refused on {}: {}", req.getRequestURI(), e.getMessage());
        return this.respond(HttpStatus.CONFLICT, e.getMessage(), req);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(BadRequestException e, HttpServletRequest req) {
        log.warn("Invalid request on {}: {}", req.getRequestURI(), e.getMessage());
        return this.respond(HttpStatus.BAD_REQUEST, e.getMessage(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), e.getMostSpecificCause().getMessage());
        return this.respond(HttpStatus.CONFLICT, "The operation conflicts with existing data (a unique value is probably already used)", req);
    }

    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ProblemDetail> handleSystem(InternalServerErrorException e, HttpServletRequest req) {
        log.error("System failure on {}", req.getRequestURI(), e);
        return this.respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception on {}", req.getRequestURI(), e);
        return this.respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req);
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(this.problem(status, message, req.getRequestURI()));
    }

    private ProblemDetail problem(HttpStatus status, String message, String path) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setType(URI.create(TYPE_PREFIX + slug(status)));
        pd.setTitle(status.getReasonPhrase());
        pd.setInstance(URI.create(path));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    private static String slug(HttpStatusCode status) {
        return HttpStatus.valueOf(status.value()).getReasonPhrase().toLowerCase().replace(' ', '-');
    }

    private static String getWebRequestPath(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
    }
}
