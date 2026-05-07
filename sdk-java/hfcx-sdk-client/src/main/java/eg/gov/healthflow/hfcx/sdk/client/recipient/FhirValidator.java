package eg.gov.healthflow.hfcx.sdk.client.recipient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eg.gov.healthflow.hfcx.sdk.core.exception.BadFhirJsonException;
import eg.gov.healthflow.hfcx.sdk.core.exception.BundleMissingTypeException;
import eg.gov.healthflow.hfcx.sdk.core.exception.NotABundleException;
import eg.gov.healthflow.hfcx.sdk.core.exception.PatientMissingNationalIdException;
import eg.gov.healthflow.hfcx.sdk.core.exception.PatientNonEgyptianException;

/**
 * Validates the structural shape of a FHIR Bundle against the Egyptian
 * Implementation Guide profile.
 *
 * <h2>Scope</h2>
 *
 * Sprint J5 ships a hand-rolled validator that enforces the rules that
 * matter for HFCX's reject-on-receipt behaviour:
 *
 * <ul>
 *   <li>Top-level resource must be a {@code Bundle}.</li>
 *   <li>Bundle must have a {@code type} field.</li>
 *   <li>Every {@code Patient} entry must have at least one {@code identifier}
 *       whose {@code system} is the Egyptian National-ID URI
 *       ({@value #NATIONAL_ID_SYSTEM}).</li>
 *   <li>Every {@code Patient} entry must declare {@code address[0].country}
 *       equal to {@code EG}.</li>
 * </ul>
 *
 * <p>This is NOT full HAPI-FHIR-based IG profile validation. The hand-
 * rolled approach keeps the SDK transitive footprint small (no
 * {@code ca.uhn.hapi.fhir:*} pulls). Once
 * {@code fhir-ig/egyptian-ig.tgz} is synced from a real platform
 * release, a follow-up sprint can swap this for HAPI-FHIR's
 * {@code FhirInstanceValidator} loaded with the IG package without
 * changing the public {@link RecipientHandler} surface.
 */
public final class FhirValidator {

    /**
     * Wire-format error codes raised on profile violations. These now point
     * at the canonical {@link eg.gov.healthflow.hfcx.sdk.core.exception.ErrorCode}
     * catalog entries; the constants are kept for backwards compatibility
     * with callers that previously imported them directly.
     */
    public static final String CODE_NOT_A_BUNDLE = NotABundleException.CODE;
    public static final String CODE_BUNDLE_MISSING_TYPE = BundleMissingTypeException.CODE;
    public static final String CODE_PATIENT_MISSING_NATIONAL_ID = PatientMissingNationalIdException.CODE;
    public static final String CODE_PATIENT_NON_EGYPTIAN = PatientNonEgyptianException.CODE;
    public static final String CODE_INVALID_JSON = BadFhirJsonException.CODE;

    /** System URI declared by the Egyptian IG for the National-ID identifier slice. */
    public static final String NATIONAL_ID_SYSTEM = "http://hcx-egypt.gov.eg/identifiers/national-id";

    private static final ObjectMapper JSON = new ObjectMapper();

    public void validate(String bundleJson) {
        if (bundleJson == null || bundleJson.isBlank()) {
            throw new BadFhirJsonException("FHIR Bundle payload is empty");
        }
        JsonNode root;
        try {
            root = JSON.readTree(bundleJson);
        } catch (JsonProcessingException e) {
            throw new BadFhirJsonException(
                    "FHIR payload is not valid JSON: " + e.getMessage());
        }

        if (!"Bundle".equals(text(root, "resourceType"))) {
            throw new NotABundleException(
                    "Top-level resource must be Bundle (got '"
                            + text(root, "resourceType") + "')");
        }
        if (text(root, "type") == null) {
            throw new BundleMissingTypeException(
                    "Bundle.type is required by the Egyptian IG");
        }

        JsonNode entry = root.path("entry");
        if (!entry.isArray()) {
            return; // Empty or absent entry array — no Patient to check.
        }
        for (JsonNode wrapper : entry) {
            JsonNode resource = wrapper.path("resource");
            if (!"Patient".equals(text(resource, "resourceType"))) {
                continue;
            }
            validatePatient(resource);
        }
    }

    private static void validatePatient(JsonNode patient) {
        JsonNode identifiers = patient.path("identifier");
        boolean hasNationalId = false;
        if (identifiers.isArray()) {
            for (JsonNode id : identifiers) {
                if (NATIONAL_ID_SYSTEM.equals(text(id, "system"))) {
                    hasNationalId = true;
                    break;
                }
            }
        }
        if (!hasNationalId) {
            throw new PatientMissingNationalIdException(
                    "Patient is missing an identifier with system "
                            + NATIONAL_ID_SYSTEM);
        }

        JsonNode addresses = patient.path("address");
        if (!addresses.isArray() || addresses.isEmpty()) {
            throw new PatientNonEgyptianException(
                    "Patient.address[0] is required by the Egyptian IG");
        }
        String country = text(addresses.get(0), "country");
        if (!"EG".equals(country)) {
            throw new PatientNonEgyptianException(
                    "Patient.address[0].country must be 'EG' (got '" + country + "')");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
