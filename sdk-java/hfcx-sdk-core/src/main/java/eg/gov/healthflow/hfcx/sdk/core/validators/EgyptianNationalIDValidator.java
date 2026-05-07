package eg.gov.healthflow.hfcx.sdk.core.validators;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Structural validator for Egyptian National-ID numbers.
 *
 * <p>An Egyptian National ID is 14 digits with the layout:
 *
 * <pre>{@code
 *   1                 century digit (2 = 1900s, 3 = 2000s)
 *   2-3               year of birth (last two digits)
 *   4-5               month of birth (01-12)
 *   6-7               day of birth (01-31)
 *   8-9               governorate code (see EgyptianGovernorate)
 *   10-13             4-digit serial; last digit's parity encodes gender
 *   14                checksum digit
 * }</pre>
 *
 * <p>This validator enforces the structural constraints (digit count,
 * century, real Gregorian date, governorate code in the known set).
 * The closing checksum digit is intentionally not verified — there is
 * no single authoritative public algorithm for it, and various civil
 * registry systems disagree on the weights used. We document this so
 * the cross-SDK invariant ("same input → same accept/reject") is
 * preserved across Java/Python/.NET/JavaScript.
 */
public final class EgyptianNationalIDValidator {

    private EgyptianNationalIDValidator() {}

    /** @return true if {@code id} is structurally valid. */
    public static boolean isValid(String id) {
        return result(id).valid();
    }

    /** Returns a richer record describing the validation outcome. */
    public static Result result(String id) {
        if (id == null || id.length() != 14) {
            return Result.invalid("must be exactly 14 digits");
        }
        for (int i = 0; i < 14; i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return Result.invalid("contains a non-digit character at position " + (i + 1));
            }
        }
        char century = id.charAt(0);
        int yearPrefix;
        switch (century) {
            case '2' -> yearPrefix = 1900;
            case '3' -> yearPrefix = 2000;
            default -> {
                return Result.invalid("century digit must be 2 or 3 (got '" + century + "')");
            }
        }
        int year = yearPrefix + Integer.parseInt(id.substring(1, 3));
        int month = Integer.parseInt(id.substring(3, 5));
        int day = Integer.parseInt(id.substring(5, 7));
        try {
            LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return Result.invalid("date of birth " + year + "-" + month + "-" + day
                    + " is not a real Gregorian date");
        }
        String govCode = id.substring(7, 9);
        if (EgyptianGovernorate.fromCode(govCode).isEmpty()) {
            return Result.invalid("governorate code '" + govCode + "' is not recognised");
        }
        char genderDigit = id.charAt(12);
        Gender gender = (genderDigit - '0') % 2 == 1 ? Gender.MALE : Gender.FEMALE;
        return new Result(true, "ok", LocalDate.of(year, month, day),
                EgyptianGovernorate.fromCode(govCode).orElseThrow(), gender);
    }

    public enum Gender { MALE, FEMALE }

    public record Result(
            boolean valid,
            String reason,
            LocalDate dateOfBirth,
            EgyptianGovernorate governorate,
            Gender gender) {

        static Result invalid(String reason) {
            return new Result(false, reason, null, null, null);
        }
    }
}
