package eg.gov.healthflow.hfcx.sdk.core.validators;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Validator for Egyptian IBANs.
 *
 * <p>Per CBE specification: {@code EG} + 2 check digits + 25 alphanumeric
 * characters (4 bank, 4 branch, 17 account) = 29 characters total. Must
 * pass the standard ISO 13616 mod-97 check.
 */
public final class EgyptianIBANValidator {

    private static final Pattern STRUCTURE = Pattern.compile("^EG\\d{2}[A-Z0-9]{25}$");
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private EgyptianIBANValidator() {}

    public static boolean isValid(String iban) {
        if (iban == null) {
            return false;
        }
        String cleaned = iban.replace(" ", "").toUpperCase();
        if (!STRUCTURE.matcher(cleaned).matches()) {
            return false;
        }
        // ISO 13616 mod-97: move first four chars to the end, replace each
        // letter with its 1-indexed position-in-the-alphabet plus 9
        // (A → 10, B → 11, ..., Z → 35), parse as a BigInteger, check mod 97 == 1.
        String reordered = cleaned.substring(4) + cleaned.substring(0, 4);
        StringBuilder numeric = new StringBuilder(reordered.length() * 2);
        for (int i = 0; i < reordered.length(); i++) {
            char c = reordered.charAt(i);
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else {
                numeric.append(c - 'A' + 10);
            }
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
