package eg.gov.healthflow.hfcx.sdk.core.validators;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The 27 Egyptian governorates and their National-ID prefix codes.
 *
 * <p>Source: Central Agency for Public Mobilization and Statistics (CAPMAS).
 * Codes are stable and identical across SDKs — cross-SDK invariant.
 */
public enum EgyptianGovernorate {
    CAIRO("01", "Cairo", "القاهرة"),
    ALEXANDRIA("02", "Alexandria", "الإسكندرية"),
    PORT_SAID("03", "Port Said", "بورسعيد"),
    SUEZ("04", "Suez", "السويس"),
    DAMIETTA("11", "Damietta", "دمياط"),
    DAKAHLIA("12", "Dakahlia", "الدقهلية"),
    SHARQIA("13", "Sharqia", "الشرقية"),
    QALYUBIA("14", "Qalyubia", "القليوبية"),
    KAFR_EL_SHEIKH("15", "Kafr El Sheikh", "كفر الشيخ"),
    GHARBIA("16", "Gharbia", "الغربية"),
    MONUFIA("17", "Monufia", "المنوفية"),
    BEHEIRA("18", "Beheira", "البحيرة"),
    ISMAILIA("19", "Ismailia", "الإسماعيلية"),
    GIZA("21", "Giza", "الجيزة"),
    BENI_SUEF("22", "Beni Suef", "بني سويف"),
    FAIYUM("23", "Faiyum", "الفيوم"),
    MINYA("24", "Minya", "المنيا"),
    ASYUT("25", "Asyut", "أسيوط"),
    SOHAG("26", "Sohag", "سوهاج"),
    QENA("27", "Qena", "قنا"),
    ASWAN("28", "Aswan", "أسوان"),
    LUXOR("29", "Luxor", "الأقصر"),
    RED_SEA("31", "Red Sea", "البحر الأحمر"),
    NEW_VALLEY("32", "New Valley", "الوادي الجديد"),
    MATRUH("33", "Matruh", "مطروح"),
    NORTH_SINAI("34", "North Sinai", "شمال سيناء"),
    SOUTH_SINAI("35", "South Sinai", "جنوب سيناء");

    private static final Map<String, EgyptianGovernorate> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(EgyptianGovernorate::code, Function.identity()));

    private final String code;
    private final String englishName;
    private final String arabicName;

    EgyptianGovernorate(String code, String englishName, String arabicName) {
        this.code = code;
        this.englishName = englishName;
        this.arabicName = arabicName;
    }

    /** Two-digit National-ID prefix code, e.g. {@code "01"} for Cairo. */
    public String code() {
        return code;
    }

    public String englishName() {
        return englishName;
    }

    public String arabicName() {
        return arabicName;
    }

    /**
     * @return the governorate matching the two-digit National-ID prefix,
     *     or empty if {@code code} is not a known Egyptian governorate.
     */
    public static Optional<EgyptianGovernorate> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
