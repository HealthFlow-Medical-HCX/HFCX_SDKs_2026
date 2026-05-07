package eg.gov.healthflow.hfcx.sdk.core.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class HfcxExceptionTest {

    @Test
    void protocolExceptionCarriesWireCode() {
        ProtocolException ex = new ProtocolException("ERR-P-001", "missing header");
        assertEquals("ERR-P-001", ex.getCode());
        assertEquals("missing header", ex.getMessage());
    }

    @Test
    void businessExceptionCarriesWireCode() {
        BusinessException ex = new BusinessException("ERR-B-006", "invalid National ID");
        assertEquals("ERR-B-006", ex.getCode());
    }

    @Test
    void technicalExceptionPropagatesCause() {
        Throwable cause = new RuntimeException("connection refused");
        TechnicalException ex = new TechnicalException("ERR-T-001", "registry unreachable", cause);
        assertEquals("ERR-T-001", ex.getCode());
        assertSame(cause, ex.getCause());
    }
}
