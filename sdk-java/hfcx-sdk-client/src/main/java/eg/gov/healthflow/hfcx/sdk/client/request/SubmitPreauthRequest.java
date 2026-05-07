package eg.gov.healthflow.hfcx.sdk.client.request;

import java.util.Objects;

/** A FHIR Bundle containing a {@code Claim} resource with {@code use=preauthorization}. */
public record SubmitPreauthRequest(String recipientCode, String preauthBundle, String correlationId)
        implements HfcxRequest {

    public SubmitPreauthRequest {
        Objects.requireNonNull(recipientCode, "recipientCode");
        Objects.requireNonNull(preauthBundle, "preauthBundle");
    }

    @Override
    public String payload() {
        return preauthBundle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String recipientCode;
        private String preauthBundle;
        private String correlationId;

        public Builder recipientCode(String recipientCode) {
            this.recipientCode = recipientCode;
            return this;
        }

        public Builder preauthBundle(String preauthBundle) {
            this.preauthBundle = preauthBundle;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public SubmitPreauthRequest build() {
            return new SubmitPreauthRequest(recipientCode, preauthBundle, correlationId);
        }
    }
}
