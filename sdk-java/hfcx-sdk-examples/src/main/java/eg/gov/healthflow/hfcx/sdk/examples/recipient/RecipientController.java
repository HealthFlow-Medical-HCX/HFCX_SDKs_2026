package eg.gov.healthflow.hfcx.sdk.examples.recipient;

import eg.gov.healthflow.hfcx.sdk.client.protocol.ProtocolHeaders;
import eg.gov.healthflow.hfcx.sdk.client.recipient.RecipientHandler;
import eg.gov.healthflow.hfcx.sdk.client.recipient.RecipientResult;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import eg.gov.healthflow.hfcx.sdk.core.exception.BusinessException;
import eg.gov.healthflow.hfcx.sdk.core.exception.HfcxException;
import eg.gov.healthflow.hfcx.sdk.core.exception.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static java.util.Map.entry;

/**
 * Spring {@code @RestController} that exposes the five HFCX inbound
 * endpoints and dispatches each to {@link RecipientHandler}.
 *
 * <p>The platform's gateway always POSTs to a path under
 * {@code /v1/...}; this controller mirrors those paths exactly so the
 * gateway can be pointed at this app without rewriting.
 */
@RestController
@RequestMapping("/v1")
public class RecipientController {

    private static final Logger log = LoggerFactory.getLogger(RecipientController.class);

    private final RecipientHandler handler;

    public RecipientController(RecipientHandler handler) {
        this.handler = handler;
    }

    @PostMapping({
            "/coverageeligibility/check",
            "/preauth/submit",
            "/claim/submit",
            "/communication/on_request",
            "/paymentnotice/notify"})
    public ResponseEntity<Map<String, String>> handle(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(ProtocolHeaders.SENDER_CODE) String senderCode,
            @RequestHeader(ProtocolHeaders.RECIPIENT_CODE) String recipientCode,
            @RequestHeader(ProtocolHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(ProtocolHeaders.TIMESTAMP) String timestamp,
            @RequestHeader(ProtocolHeaders.API_CALL_ID) String apiCallId,
            @RequestBody String envelope) {
        Map<String, String> protoHeaders = Map.of(
                ProtocolHeaders.SENDER_CODE, senderCode,
                ProtocolHeaders.RECIPIENT_CODE, recipientCode,
                ProtocolHeaders.CORRELATION_ID, correlationId,
                ProtocolHeaders.TIMESTAMP, timestamp,
                ProtocolHeaders.API_CALL_ID, apiCallId);

        RecipientResult result = handler.handle(authorization, protoHeaders, envelope);

        log.info("recipient: business logic would now process the bundle "
                + "(payload size={} bytes)", result.decryptedPayload().length());

        // The HFCX protocol requires HTTP 202 with the correlation ID echoed.
        // The recipient's business logic processes the payload asynchronously.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("correlation_id", result.correlationId(), "status", "accepted"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> onAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody(ex));
    }

    @ExceptionHandler(ProtocolException.class)
    public ResponseEntity<Map<String, Object>> onProtocol(ProtocolException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(ex));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> onBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorBody(ex));
    }

    @ExceptionHandler(HfcxException.class)
    public ResponseEntity<Map<String, Object>> onTechnical(HfcxException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(ex));
    }

    /**
     * Wire format the gateway uses, as parsed by {@code HfcxClient}:
     * {@code {"error": {"code": "ERR-X-NNN", "message": "..."}}}.
     */
    private static Map<String, Object> errorBody(HfcxException ex) {
        return Map.of("error",
                Map.ofEntries(
                        entry("code", ex.getCode()),
                        entry("message", ex.getMessage() != null ? ex.getMessage() : "")));
    }
}
