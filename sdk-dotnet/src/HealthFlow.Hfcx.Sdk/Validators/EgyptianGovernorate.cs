// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Collections.Generic;

namespace HealthFlow.Hfcx.Sdk.Validators;

/// <summary>
/// The 27 Egyptian governorates and their National-ID prefix codes.
/// Source: CAPMAS. Codes are stable and identical across SDKs —
/// cross-SDK invariant. Sister to the Java SDK's
/// <c>EgyptianGovernorate</c> enum and the Python SDK's
/// <c>EgyptianGovernorate</c>.
/// </summary>
public sealed class EgyptianGovernorate
{
    public static readonly EgyptianGovernorate Cairo = new("01", "CAIRO", "Cairo", "القاهرة");
    public static readonly EgyptianGovernorate Alexandria = new("02", "ALEXANDRIA", "Alexandria", "الإسكندرية");
    public static readonly EgyptianGovernorate PortSaid = new("03", "PORT_SAID", "Port Said", "بورسعيد");
    public static readonly EgyptianGovernorate Suez = new("04", "SUEZ", "Suez", "السويس");
    public static readonly EgyptianGovernorate Damietta = new("11", "DAMIETTA", "Damietta", "دمياط");
    public static readonly EgyptianGovernorate Dakahlia = new("12", "DAKAHLIA", "Dakahlia", "الدقهلية");
    public static readonly EgyptianGovernorate Sharqia = new("13", "SHARQIA", "Sharqia", "الشرقية");
    public static readonly EgyptianGovernorate Qalyubia = new("14", "QALYUBIA", "Qalyubia", "القليوبية");
    public static readonly EgyptianGovernorate KafrElSheikh = new("15", "KAFR_EL_SHEIKH", "Kafr El Sheikh", "كفر الشيخ");
    public static readonly EgyptianGovernorate Gharbia = new("16", "GHARBIA", "Gharbia", "الغربية");
    public static readonly EgyptianGovernorate Monufia = new("17", "MONUFIA", "Monufia", "المنوفية");
    public static readonly EgyptianGovernorate Beheira = new("18", "BEHEIRA", "Beheira", "البحيرة");
    public static readonly EgyptianGovernorate Ismailia = new("19", "ISMAILIA", "Ismailia", "الإسماعيلية");
    public static readonly EgyptianGovernorate Giza = new("21", "GIZA", "Giza", "الجيزة");
    public static readonly EgyptianGovernorate BeniSuef = new("22", "BENI_SUEF", "Beni Suef", "بني سويف");
    public static readonly EgyptianGovernorate Faiyum = new("23", "FAIYUM", "Faiyum", "الفيوم");
    public static readonly EgyptianGovernorate Minya = new("24", "MINYA", "Minya", "المنيا");
    public static readonly EgyptianGovernorate Asyut = new("25", "ASYUT", "Asyut", "أسيوط");
    public static readonly EgyptianGovernorate Sohag = new("26", "SOHAG", "Sohag", "سوهاج");
    public static readonly EgyptianGovernorate Qena = new("27", "QENA", "Qena", "قنا");
    public static readonly EgyptianGovernorate Aswan = new("28", "ASWAN", "Aswan", "أسوان");
    public static readonly EgyptianGovernorate Luxor = new("29", "LUXOR", "Luxor", "الأقصر");
    public static readonly EgyptianGovernorate RedSea = new("31", "RED_SEA", "Red Sea", "البحر الأحمر");
    public static readonly EgyptianGovernorate NewValley = new("32", "NEW_VALLEY", "New Valley", "الوادي الجديد");
    public static readonly EgyptianGovernorate Matruh = new("33", "MATRUH", "Matruh", "مطروح");
    public static readonly EgyptianGovernorate NorthSinai = new("34", "NORTH_SINAI", "North Sinai", "شمال سيناء");
    public static readonly EgyptianGovernorate SouthSinai = new("35", "SOUTH_SINAI", "South Sinai", "جنوب سيناء");

    private static readonly IReadOnlyList<EgyptianGovernorate> _all =
    [
        Cairo, Alexandria, PortSaid, Suez,
        Damietta, Dakahlia, Sharqia, Qalyubia, KafrElSheikh, Gharbia,
        Monufia, Beheira, Ismailia,
        Giza, BeniSuef, Faiyum, Minya, Asyut, Sohag, Qena, Aswan, Luxor,
        RedSea, NewValley, Matruh, NorthSinai, SouthSinai,
    ];

    private static readonly Dictionary<string, EgyptianGovernorate> _byCode = BuildIndex();

    /// <summary>Every defined governorate (27).</summary>
    public static IReadOnlyList<EgyptianGovernorate> All => _all;

    /// <summary>Two-digit National-ID prefix code, e.g. <c>"01"</c> for Cairo.</summary>
    public string Code { get; }

    /// <summary>Stable internal name, e.g. <c>"CAIRO"</c>. Cross-SDK invariant.</summary>
    public string Name { get; }

    /// <summary>Common English name, e.g. <c>"Cairo"</c>.</summary>
    public string EnglishName { get; }

    /// <summary>Common Arabic name, e.g. <c>"القاهرة"</c>.</summary>
    public string ArabicName { get; }

    private EgyptianGovernorate(string code, string name, string englishName, string arabicName)
    {
        Code = code;
        Name = name;
        EnglishName = englishName;
        ArabicName = arabicName;
    }

    /// <summary>
    /// Look up a governorate by its two-digit National-ID prefix.
    /// Returns <see langword="null"/> when no entry matches.
    /// </summary>
    public static EgyptianGovernorate? FromCode(string? code)
    {
        if (string.IsNullOrEmpty(code))
        {
            return null;
        }

        return _byCode.TryGetValue(code, out var entry) ? entry : null;
    }

    /// <inheritdoc />
    public override string ToString() => Name;

    private static Dictionary<string, EgyptianGovernorate> BuildIndex()
    {
        var dict = new Dictionary<string, EgyptianGovernorate>(_all.Count, System.StringComparer.Ordinal);
        foreach (var g in _all)
        {
            dict[g.Code] = g;
        }

        return dict;
    }
}
