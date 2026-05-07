package eg.gov.healthflow.hfcx.sdk.client.request;

import java.util.Objects;

/** A FHIR Bundle containing a {@code Claim} resource for asynchronous adjudication. */
public record SubmitClaimRequest(String recipientCode, String claimBundle, String correlationId)
        implements HfcxRequest {

    public SubmitClaimRequest {
        Objects.requireNonNull(recipientCode, "recipientCode");
        Objects.requireNonNull(claimBundle, "claimBundle");
        // correlationId is optional — the SDK auto-generates one if null.
    }

    @Override
    public String payload() {
        return claimBundle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String recipientCode;
        private String claimBundle;
        private String correlationId;

        public Builder recipientCode(String recipientCode) {
            this.recipientCode = recipientCode;
            return this;
        }

        public Builder claimBundle(String claimBundle) {
            this.claimBundle = claimBundle;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public SubmitClaimRequest build() {
            return new SubmitClaimRequest(recipientCode, claimBundle, correlationId);
        }
    }
}
