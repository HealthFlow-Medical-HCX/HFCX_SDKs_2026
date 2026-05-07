package eg.gov.healthflow.hfcx.sdk.client.recipient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eg.gov.healthflow.hfcx.sdk.core.exception.BusinessException;
import eg.gov.healthflow.hfcx.sdk.core.validators.EgyptianIBANValidator;
import eg.gov.healthflow.hfcx.sdk.core.validators.EgyptianNationalIDValidator;
import eg.gov.healthflow.hfcx.sdk.core.validators.EgyptianPhoneValidator;

/**
 * Walks a FHIR Bundle and runs the Egyptian-specific value-level
 * validators (National ID, phone, IBAN) on the relevant resource fields.
 *
 * <p>Where {@link FhirValidator} answers "does this Bundle have the
 * required structure?", this class answers "do the Egyptian-specific
 * values inside it look real?".
 */
public final class EgyptianBundleValidator {

    public static final String CODE_BAD_NATIONAL_ID = "ERR-B-EG-001";
    public static final String CODE_BAD_PHONE = "ERR-B-EG-002";
    public static final String CODE_BAD_IBAN = "ERR-B-EG-003";

    private static final ObjectMapper JSON = new ObjectMapper();

    public void validate(String bundleJson) {
        if (bundleJson == null || bundleJson.isBlank()) {
            return;
        }
        JsonNode root;
        try {
            root = JSON.readTree(bundleJson);
        } catch (JsonProcessingException e) {
            // FhirValidator runs first and catches malformed JSON; if we
            // somehow got here, fail-secure.
            throw new BusinessException("ERR-B-FHIR-005",
                    "FHIR payload is not valid JSON: " + e.getMessage());
        }

        JsonNode entry = root.path("entry");
        if (!entry.isArray()) {
            return;
        }
        for (JsonNode wrapper : entry) {
            JsonNode resource = wrapper.path("resource");
            String resourceType = textOrNull(resource, "resourceType");
            if ("Patient".equals(resourceType)) {
                validatePatient(resource);
            } else if ("Organization".equals(resourceType)) {
                validateOrganization(resource);
            }
        }
    }

    private static void validatePatient(JsonNode patient) {
        JsonNode identifiers = patient.path("identifier");
        if (identifiers.isArray()) {
            for (JsonNode id : identifiers) {
                if (FhirValidator.NATIONAL_ID_SYSTEM.equals(textOrNull(id, "system"))) {
                    String value = textOrNull(id, "value");
                    if (!EgyptianNationalIDValidator.isValid(value)) {
                        throw new BusinessException(CODE_BAD_NATIONAL_ID,
                                "Patient National-ID identifier value '" + value
                                        + "' is not a valid Egyptian National ID");
                    }
                }
            }
        }
        JsonNode telecom = patient.path("telecom");
        if (telecom.isArray()) {
            for (JsonNode contact : telecom) {
                if ("phone".equals(textOrNull(contact, "system"))) {
                    String value = textOrNull(contact, "value");
                    if (!EgyptianPhoneValidator.isValid(value)) {
                        throw new BusinessException(CODE_BAD_PHONE,
                                "Patient phone '" + value
                                        + "' is not a valid Egyptian mobile number");
                    }
                }
            }
        }
    }

    private static void validateOrganization(JsonNode org) {
        // Egyptian IG carries IBAN on Organization.identifier with a
        // matching system URI; check any identifier whose value looks
        // like it should be an IBAN.
        JsonNode identifiers = org.path("identifier");
        if (!identifiers.isArray()) {
            return;
        }
        for (JsonNode id : identifiers) {
            String system = textOrNull(id, "system");
            if (system != null && system.contains("iban")) {
                String value = textOrNull(id, "value");
                if (!EgyptianIBANValidator.isValid(value)) {
                    throw new BusinessException(CODE_BAD_IBAN,
                            "Organization IBAN '" + value + "' is not a valid Egyptian IBAN");
                }
            }
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
