package com.llizzard.switchboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.llizzard.switchboard.SwitchboardNotAvailableException;
import com.llizzard.switchboard.SwitchboardUnresolvedException;

/** Plain JUnit: every field of contracts/http-errors.md. */
class SwitchboardExceptionHandlerTest {

    private static final String PAYLOAD_MARKER = "SECRET-PAYLOAD-MARKER";

    private final SwitchboardExceptionHandler handler = new SwitchboardExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/refunds");

    @Test
    void unresolvedBecomes501ProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnresolved(new SwitchboardUnresolvedException("refunds"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(501);
        assertThat(problem.getType()).isEqualTo(URI.create("tag:llizzard.com,2026:switchboard:use-case-unresolved"));
        assertThat(problem.getTitle()).isEqualTo("Use case not resolved");
        assertThat(problem.getDetail())
                .isEqualTo("No resolver in group 'refunds' identified a use case for this request.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/refunds"));
        assertThat(problem.getProperties())
                .containsEntry("errorCode", "USE_CASE_UNRESOLVED")
                .containsEntry("group", "refunds")
                .doesNotContainKey("key");
    }

    @Test
    void notAvailableBecomes404ProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleNotAvailable(new SwitchboardNotAvailableException("refunds", "EXPEDITED"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ProblemDetail problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getType()).isEqualTo(URI.create("tag:llizzard.com,2026:switchboard:use-case-not-available"));
        assertThat(problem.getTitle()).isEqualTo("Use case not available");
        assertThat(problem.getDetail())
                .isEqualTo("Use case 'EXPEDITED' in group 'refunds' is not registered or is disabled.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/refunds"));
        assertThat(problem.getProperties())
                .containsEntry("errorCode", "USE_CASE_NOT_AVAILABLE")
                .containsEntry("group", "refunds")
                .containsEntry("key", "EXPEDITED");
    }

    @Test
    void publicConstantsMatchTheContract() {
        assertThat(SwitchboardExceptionHandler.ERROR_CODE_UNRESOLVED).isEqualTo("USE_CASE_UNRESOLVED");
        assertThat(SwitchboardExceptionHandler.ERROR_CODE_NOT_AVAILABLE).isEqualTo("USE_CASE_NOT_AVAILABLE");
        assertThat(SwitchboardExceptionHandler.TYPE_UNRESOLVED)
                .isEqualTo(URI.create("tag:llizzard.com,2026:switchboard:use-case-unresolved"));
        assertThat(SwitchboardExceptionHandler.TYPE_NOT_AVAILABLE)
                .isEqualTo(URI.create("tag:llizzard.com,2026:switchboard:use-case-not-available"));
    }

    @Test
    void detailNeverContainsPayloadData() {
        // The payload carries the marker; the exceptions only ever see group and key.
        request.setContent(("{\"orderId\":\"" + PAYLOAD_MARKER + "\"}").getBytes());

        ProblemDetail unresolved = handler.handleUnresolved(new SwitchboardUnresolvedException("refunds"), request)
                .getBody();
        ProblemDetail notAvailable = handler
                .handleNotAvailable(new SwitchboardNotAvailableException("refunds", "NOPE"), request).getBody();

        assertThat(unresolved.getDetail()).doesNotContain(PAYLOAD_MARKER);
        assertThat(notAvailable.getDetail()).doesNotContain(PAYLOAD_MARKER);
        assertThat(unresolved.getProperties().values()).doesNotContain(PAYLOAD_MARKER);
        assertThat(notAvailable.getProperties().values()).doesNotContain(PAYLOAD_MARKER);
    }

    @Test
    void keyAtTheLengthLimitIsEchoed() {
        String key = "K".repeat(64);

        ProblemDetail problem = handler
                .handleNotAvailable(new SwitchboardNotAvailableException("refunds", key), request).getBody();

        assertThat(problem.getProperties()).containsEntry("key", key);
        assertThat(problem.getDetail()).contains("'" + key + "'");
    }

    @Test
    void keyOverTheLengthLimitIsNotEchoed() {
        String key = "K".repeat(65);

        ProblemDetail problem = handler
                .handleNotAvailable(new SwitchboardNotAvailableException("refunds", key), request).getBody();

        assertNotEchoed(problem, key);
    }

    @Test
    void keyOutsideTheKeyFormatIsNotEchoed() {
        for (String key : new String[] { "<script>alert(1)</script>", "has space", "dot.ted", "ünïcode", "" }) {
            ProblemDetail problem = handler
                    .handleNotAvailable(new SwitchboardNotAvailableException("refunds", key), request).getBody();

            assertNotEchoed(problem, key);
        }
    }

    private static void assertNotEchoed(ProblemDetail problem, String key) {
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getProperties())
                .containsEntry("errorCode", "USE_CASE_NOT_AVAILABLE")
                .containsEntry("group", "refunds")
                .doesNotContainKey("key");
        assertThat(problem.getDetail())
                .isEqualTo("The resolved use case in group 'refunds' is not registered or is disabled.");
        if (!key.isEmpty()) {
            assertThat(problem.getDetail()).doesNotContain(key);
        }
    }

    @Test
    void instanceIsOmittedWhenTheRequestUriIsNotAValidUri() {
        MockHttpServletRequest odd = new MockHttpServletRequest("POST", "/api/re funds|x");

        ProblemDetail problem = handler.handleUnresolved(new SwitchboardUnresolvedException("refunds"), odd).getBody();

        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).containsEntry("errorCode", "USE_CASE_UNRESOLVED");
    }
}
