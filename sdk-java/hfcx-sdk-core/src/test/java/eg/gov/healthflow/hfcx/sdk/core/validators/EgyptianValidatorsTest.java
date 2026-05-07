package eg.gov.healthflow.hfcx.sdk.core.validators;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EgyptianValidatorsTest {

    // ---------------- EgyptianGovernorate ----------------

    @Test
    void governorateEnumHasExactly27Entries() {
        assertEquals(27, EgyptianGovernorate.values().length,
                "27 governorates is the cross-SDK invariant");
    }

    @Test
    void governorateLookupByCodeFindsKnownPrefixes() {
        assertEquals(EgyptianGovernorate.CAIRO, EgyptianGovernorate.fromCode("01").orElseThrow());
        assertEquals(EgyptianGovernorate.GIZA, EgyptianGovernorate.fromCode("21").orElseThrow());
        assertEquals(EgyptianGovernorate.SOUTH_SINAI, EgyptianGovernorate.fromCode("35").orElseThrow());
        assertTrue(EgyptianGovernorate.fromCode("00").isEmpty());
        assertTrue(EgyptianGovernorate.fromCode(null).isEmpty());
        assertTrue(EgyptianGovernorate.fromCode("99").isEmpty());
    }

    // ---------------- EgyptianNationalIDValidator ----------------

    @Test
    void nationalIdHappyPaths() {
        // 1995-04-15, Cairo (01), serial 1234, check 5
        assertTrue(EgyptianNationalIDValidator.isValid("29504150112345"));
        // 2003-12-31, Giza (21)
        assertTrue(EgyptianNationalIDValidator.isValid("30312312112340"));
        // 1999-02-28, Alexandria (02), male serial
        assertTrue(EgyptianNationalIDValidator.isValid("29902280298765"));
    }

    @Test
    void nationalIdRichResultExposesParsedFields() {
        EgyptianNationalIDValidator.Result r =
                EgyptianNationalIDValidator.result("29504150112345");
        assertTrue(r.valid());
        assertEquals(1995, r.dateOfBirth().getYear());
        assertEquals(4, r.dateOfBirth().getMonthValue());
        assertEquals(15, r.dateOfBirth().getDayOfMonth());
        assertEquals(EgyptianGovernorate.CAIRO, r.governorate());
    }

    @Test
    void nationalIdRejectsWrongLength() {
        assertFalse(EgyptianNationalIDValidator.isValid("123"));
        assertFalse(EgyptianNationalIDValidator.isValid("295041501123450")); // 15
        assertFalse(EgyptianNationalIDValidator.isValid("2950415011234")); // 13
        assertFalse(EgyptianNationalIDValidator.isValid(""));
        assertFalse(EgyptianNationalIDValidator.isValid(null));
    }

    @Test
    void nationalIdRejectsNonDigit() {
        assertFalse(EgyptianNationalIDValidator.isValid("2950415011234A"));
        assertFalse(EgyptianNationalIDValidator.isValid("29504X50112345"));
    }

    @Test
    void nationalIdRejectsUnknownCenturyDigit() {
        assertFalse(EgyptianNationalIDValidator.isValid("19504150112345")); // century 1
        assertFalse(EgyptianNationalIDValidator.isValid("49504150112345")); // century 4
    }

    @Test
    void nationalIdRejectsImpossibleDate() {
        assertFalse(EgyptianNationalIDValidator.isValid("29513320112345")); // month 13
        assertFalse(EgyptianNationalIDValidator.isValid("29502310112345")); // Feb 31
        assertFalse(EgyptianNationalIDValidator.isValid("29502290112345")); // 1995 not leap
        assertFalse(EgyptianNationalIDValidator.isValid("29504310112345")); // April 31
    }

    @Test
    void nationalIdRejectsUnknownGovernorate() {
        assertFalse(EgyptianNationalIDValidator.isValid("29504150512345")); // gov 05
        assertFalse(EgyptianNationalIDValidator.isValid("29504159912345")); // gov 99
    }

    @Test
    void nationalIdGenderInferredFromSerialDigit() {
        // Position 13 (1-indexed) is the gender digit: odd → male, even → female.
        EgyptianNationalIDValidator.Result male = EgyptianNationalIDValidator.result("29504150112355");
        EgyptianNationalIDValidator.Result female = EgyptianNationalIDValidator.result("29504150112365");
        assertEquals(EgyptianNationalIDValidator.Gender.MALE, male.gender());
        assertEquals(EgyptianNationalIDValidator.Gender.FEMALE, female.gender());
    }

    // ---------------- EgyptianPhoneValidator ----------------

    @Test
    void phoneAcceptsAllFourCanonicalForms() {
        assertEquals("+201012345678", EgyptianPhoneValidator.normalise("+201012345678"));
        assertEquals("+201012345678", EgyptianPhoneValidator.normalise("00201012345678"));
        assertEquals("+201012345678", EgyptianPhoneValidator.normalise("201012345678"));
        assertEquals("+201012345678", EgyptianPhoneValidator.normalise("01012345678"));
    }

    @Test
    void phoneAcceptsAllFourMobilePrefixes() {
        assertTrue(EgyptianPhoneValidator.isValid("01012345678"));
        assertTrue(EgyptianPhoneValidator.isValid("01112345678"));
        assertTrue(EgyptianPhoneValidator.isValid("01212345678"));
        assertTrue(EgyptianPhoneValidator.isValid("01512345678"));
    }

    @Test
    void phoneStripsWhitespaceAndHyphens() {
        assertEquals("+201012345678", EgyptianPhoneValidator.normalise("+20 10 1234 5678"));
        assertEquals("+201012345678", EgyptianPhoneValidator.normalise("0101-234-5678"));
    }

    @Test
    void phoneRejectsBadInputs() {
        assertFalse(EgyptianPhoneValidator.isValid(null));
        assertFalse(EgyptianPhoneValidator.isValid(""));
        assertFalse(EgyptianPhoneValidator.isValid("01312345678"));   // 013 not a mobile prefix
        assertFalse(EgyptianPhoneValidator.isValid("0101234567"));    // 10 digits, too short
        assertFalse(EgyptianPhoneValidator.isValid("010123456789"));  // 12 digits, too long
        assertFalse(EgyptianPhoneValidator.isValid("11012345678"));   // doesn't start with 0/+/2
        assertFalse(EgyptianPhoneValidator.isValid("+30101234567"));  // wrong country code
    }

    // ---------------- EgyptianIBANValidator ----------------

    @Test
    void ibanAcceptsKnownValidExample() {
        // Reference example from public CBE documentation.
        assertTrue(EgyptianIBANValidator.isValid("EG380019000500000000263180002"));
    }

    @Test
    void ibanIsCaseInsensitiveAndStripsSpaces() {
        assertTrue(EgyptianIBANValidator.isValid("eg38 0019 0005 0000 0000 2631 8000 2"));
    }

    @Test
    void ibanRejectsBadInputs() {
        assertFalse(EgyptianIBANValidator.isValid(null));
        assertFalse(EgyptianIBANValidator.isValid(""));
        assertFalse(EgyptianIBANValidator.isValid("EG380019000500000000263180003")); // bad check
        assertFalse(EgyptianIBANValidator.isValid("FR380019000500000000263180002")); // not EG
        assertFalse(EgyptianIBANValidator.isValid("EG3800190005000000002631800022")); // 30 chars
        assertFalse(EgyptianIBANValidator.isValid("EG380019000500000000263180")); // 26 chars
        assertFalse(EgyptianIBANValidator.isValid("EG3800190005000000002631800!2")); // bad char
    }
}
