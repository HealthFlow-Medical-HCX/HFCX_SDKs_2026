package eg.gov.healthflow.hfcx.sdk.client.request;

import java.util.Objects;

/** A FHIR {@code CoverageEligibilityRequest} resource (or a Bundle wrapping one). */
public record CheckEligibilityRequest(String recipientCode, String eligibilityBundle, String correlationId)
        implements HfcxRequest {

    public CheckEligibilityRequest {
        Objects.requireNonNull(recipientCode, "recipientCode");
        Objects.requireNonNull(eligibilityBundle, "eligibilityBundle");
    }

    @Override
    public String payload() {
        return eligibilityBundle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String recipientCode;
        private String eligibilityBundle;
        private String correlationId;

        public Builder recipientCode(String recipientCode) {
            this.recipientCode = recipientCode;
            return this;
        }

        public Builder eligibilityBundle(String eligibilityBundle) {
            this.eligibilityBundle = eligibilityBundle;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public CheckEligibilityRequest build() {
            return new CheckEligibilityRequest(recipientCode, eligibilityBundle, correlationId);
        }
    }
}
