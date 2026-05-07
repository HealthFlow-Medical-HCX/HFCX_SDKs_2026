package eg.gov.healthflow.hfcx.sdk.core.validators;

import java.util.regex.Pattern;

/**
 * Validator and normaliser for Egyptian mobile phone numbers.
 *
 * <p>Accepts the four common forms:
 *
 * <ul>
 *   <li>{@code +201[0125]XXXXXXXX} — international with leading {@code +}</li>
 *   <li>{@code 00201[0125]XXXXXXXX} — international with double-zero prefix</li>
 *   <li>{@code 201[0125]XXXXXXXX} — bare country code</li>
 *   <li>{@code 01[0125]XXXXXXXX} — local form with leading zero</li>
 * </ul>
 *
 * <p>Mobile network prefixes are {@code 010} (Vodafone), {@code 011}
 * (Etisalat), {@code 012} (Orange), {@code 015} (WE).
 */
public final class EgyptianPhoneValidator {

    private static final Pattern NORMALISED = Pattern.compile("^\\+201[0125]\\d{8}$");

    private EgyptianPhoneValidator() {}

    public static boolean isValid(String phone) {
        return normalise(phone) != null;
    }

    /**
     * @return the canonical {@code +201XXXXXXXXX} form, or {@code null} if
     *     {@code phone} is not a valid Egyptian mobile number.
     */
    public static String normalise(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim().replace(" ", "").replace("-", "");
        String candidate;
        if (trimmed.startsWith("+20")) {
            candidate = trimmed;
        } else if (trimmed.startsWith("0020")) {
            candidate = "+20" + trimmed.substring(4);
        } else if (trimmed.startsWith("20") && trimmed.length() == 12) {
            candidate = "+" + trimmed;
        } else if (trimmed.startsWith("0") && trimmed.length() == 11) {
            candidate = "+20" + trimmed.substring(1);
        } else {
            return null;
        }
        return NORMALISED.matcher(candidate).matches() ? candidate : null;
    }
}
