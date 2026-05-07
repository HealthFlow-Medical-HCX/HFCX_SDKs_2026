package eg.gov.healthflow.hfcx.sdk.client.request;

import java.util.Objects;

/** A FHIR Bundle containing a {@code PaymentNotice} resource. */
public record NotifyPaymentRequest(String recipientCode, String paymentNoticeBundle, String correlationId)
        implements HfcxRequest {

    public NotifyPaymentRequest {
        Objects.requireNonNull(recipientCode, "recipientCode");
        Objects.requireNonNull(paymentNoticeBundle, "paymentNoticeBundle");
    }

    @Override
    public String payload() {
        return paymentNoticeBundle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String recipientCode;
        private String paymentNoticeBundle;
        private String correlationId;

        public Builder recipientCode(String recipientCode) {
            this.recipientCode = recipientCode;
            return this;
        }

        public Builder paymentNoticeBundle(String paymentNoticeBundle) {
            this.paymentNoticeBundle = paymentNoticeBundle;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public NotifyPaymentRequest build() {
            return new NotifyPaymentRequest(recipientCode, paymentNoticeBundle, correlationId);
        }
    }
}
