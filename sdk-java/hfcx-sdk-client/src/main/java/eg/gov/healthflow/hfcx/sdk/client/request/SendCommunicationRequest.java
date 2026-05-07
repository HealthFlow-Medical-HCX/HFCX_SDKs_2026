package eg.gov.healthflow.hfcx.sdk.client.request;

import java.util.Objects;

/** A FHIR Bundle containing a {@code Communication} resource. */
public record SendCommunicationRequest(String recipientCode, String communicationBundle, String correlationId)
        implements HfcxRequest {

    public SendCommunicationRequest {
        Objects.requireNonNull(recipientCode, "recipientCode");
        Objects.requireNonNull(communicationBundle, "communicationBundle");
    }

    @Override
    public String payload() {
        return communicationBundle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String recipientCode;
        private String communicationBundle;
        private String correlationId;

        public Builder recipientCode(String recipientCode) {
            this.recipientCode = recipientCode;
            return this;
        }

        public Builder communicationBundle(String communicationBundle) {
            this.communicationBundle = communicationBundle;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public SendCommunicationRequest build() {
            return new SendCommunicationRequest(recipientCode, communicationBundle, correlationId);
        }
    }
}
