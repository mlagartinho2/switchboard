package com.llizzard.switchboard.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.llizzard.switchboard.SwitchboardDispatchException;
import com.llizzard.switchboard.SwitchboardNotAvailableException;
import com.llizzard.switchboard.SwitchboardUnresolvedException;

/**
 * Turns the two Switchboard dispatch failures into RFC 9457 problem responses
 * ({@code application/problem+json}):
 * <ul>
 *   <li>{@link SwitchboardUnresolvedException} → 501, {@value #ERROR_CODE_UNRESOLVED};</li>
 *   <li>{@link SwitchboardNotAvailableException} → 404, {@value #ERROR_CODE_NOT_AVAILABLE}.</li>
 * </ul>
 *
 * <p>Runs at the highest precedence so a consumer's catch-all {@code @ExceptionHandler} cannot
 * swallow these two exceptions. It handles nothing else: exceptions from consumer strategies and
 * resolvers reach the application's normal error handling.
 *
 * <p>The error codes and type URIs are a stable public contract. Public and non-final so a
 * consuming application can replace the format by defining its own bean of this type (FR-029).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SwitchboardExceptionHandler {

    /** Error code for "no resolver identified a use case" (HTTP 501). */
    public static final String ERROR_CODE_UNRESOLVED = "USE_CASE_UNRESOLVED";

    /** Error code for "use case not registered or disabled" (HTTP 404). */
    public static final String ERROR_CODE_NOT_AVAILABLE = "USE_CASE_NOT_AVAILABLE";

    /** Problem type for {@link #ERROR_CODE_UNRESOLVED}. */
    public static final URI TYPE_UNRESOLVED = URI.create("tag:llizzard.com,2026:switchboard:use-case-unresolved");

    /** Problem type for {@link #ERROR_CODE_NOT_AVAILABLE}. */
    public static final URI TYPE_NOT_AVAILABLE = URI.create("tag:llizzard.com,2026:switchboard:use-case-not-available");

    /** Longest resolved key echoed back in {@code key} and {@code detail}. */
    private static final int MAX_ECHOED_KEY_LENGTH = 64;

    /** The use-case key format (rule V2); a key outside it can never match a strategy. */
    private static final Pattern ECHOABLE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    /** Creates the handler; registered by Switchboard auto-configuration. */
    public SwitchboardExceptionHandler() {
    }

    /**
     * Answers 501 when no resolver identified a use case.
     *
     * @param ex the failure
     * @param request the current request, used for {@code instance}
     * @return a 501 problem response
     */
    @ExceptionHandler(SwitchboardUnresolvedException.class)
    public ResponseEntity<ProblemDetail> handleUnresolved(SwitchboardUnresolvedException ex,
            HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.NOT_IMPLEMENTED, TYPE_UNRESOLVED, "Use case not resolved",
                ERROR_CODE_UNRESOLVED, ex.getMessage(), ex, request);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(problem);
    }

    /**
     * Answers 404 when the resolved use case is not registered or is disabled.
     *
     * <p>The resolved key is echoed in {@code key} and {@code detail} only when it matches
     * {@code [A-Za-z0-9_-]+} and is at most 64 characters long; otherwise both leave it out.
     *
     * @param ex the failure
     * @param request the current request, used for {@code instance}
     * @return a 404 problem response
     */
    @ExceptionHandler(SwitchboardNotAvailableException.class)
    public ResponseEntity<ProblemDetail> handleNotAvailable(SwitchboardNotAvailableException ex,
            HttpServletRequest request) {
        // The key often comes straight from a client header: echo it only when it has the key format
        // and a sane length, so arbitrary client input is never reflected back.
        boolean echoKey = isEchoable(ex.caseKey());
        String detail = echoKey ? ex.getMessage()
                : "The resolved use case in group '" + ex.group() + "' is not registered or is disabled.";
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, TYPE_NOT_AVAILABLE, "Use case not available",
                ERROR_CODE_NOT_AVAILABLE, detail, ex, request);
        if (echoKey) {
            problem.setProperty("key", ex.caseKey());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, URI type, String title, String errorCode,
            String detail, SwitchboardDispatchException ex, HttpServletRequest request) {
        // The detail names only the group (and key); it never contains payload data.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        URI instance = instance(request);
        if (instance != null) {
            problem.setInstance(instance);
        }
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("group", ex.group());
        return problem;
    }

    private static boolean isEchoable(String key) {
        return key.length() <= MAX_ECHOED_KEY_LENGTH && ECHOABLE_KEY.matcher(key).matches();
    }

    private static URI instance(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        try {
            return new URI(path);
        }
        catch (URISyntaxException ex) {
            return null;
        }
    }
}
