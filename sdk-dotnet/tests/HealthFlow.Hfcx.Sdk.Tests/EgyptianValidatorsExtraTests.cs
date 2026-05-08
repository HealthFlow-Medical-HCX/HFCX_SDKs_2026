// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Collections.Generic;
using System.Linq;
using HealthFlow.Hfcx.Sdk.Validators;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Extra parametric corner-case coverage for the Egyptian validators —
/// every governorate code, leap-year boundaries, every gender digit,
/// every mobile prefix, IBAN canonical / corrupted forms. Sister to
/// Python's <c>test_egyptian_validators_extra.py</c>.
/// </summary>
public class EgyptianValidatorsExtraTests
{
    public static IEnumerable<object[]> EveryGovernorate =>
        EgyptianGovernorate.All.Select(g => new object[] { g });

    [Theory]
    [MemberData(nameof(EveryGovernorate))]
    public void EveryGovernorate_RoundTripsThroughFromCode(EgyptianGovernorate g)
    {
        Assert.Same(g, EgyptianGovernorate.FromCode(g.Code));
    }

    [Theory]
    [MemberData(nameof(EveryGovernorate))]
    public void EveryGovernorate_HasEnglishAndArabicName(EgyptianGovernorate g)
    {
        Assert.False(string.IsNullOrEmpty(g.EnglishName));
        Assert.False(string.IsNullOrEmpty(g.ArabicName));
    }

    [Theory]
    [InlineData("")]
    [InlineData("0")]
    [InlineData("001")]
    [InlineData("0a")]
    [InlineData("  ")]
    [InlineData("00")]
    [InlineData("10")]
    [InlineData("20")]
    [InlineData("30")]
    [InlineData("99")]
    public void UnrecognisedGovernorateCodes_ReturnNull(string code)
    {
        Assert.Null(EgyptianGovernorate.FromCode(code));
    }

    // ── National-ID parametrics ────────────────────────────────────

    [Theory]
    [MemberData(nameof(EveryGovernorate))]
    public void EveryGovernorate_ProducesValidNid(EgyptianGovernorate g)
    {
        // 1995-06-15 + governorate + serial 12341 (gender male)
        var nid = "29506" + "15" + g.Code + "12341";
        Assert.True(EgyptianNationalIdValidator.IsValid(nid));
        var parsed = EgyptianNationalIdValidator.Parse(nid);
        Assert.Same(g, parsed.Governorate);
    }

    [Fact]
    public void Century2_Yields19xxYearPrefix()
    {
        var parsed = EgyptianNationalIdValidator.Parse("29504150112345");
        Assert.Equal(1995, parsed.DateOfBirth!.Value.Year);
    }

    [Fact]
    public void Century3_Yields20xxYearPrefix()
    {
        var parsed = EgyptianNationalIdValidator.Parse("30312312112345");
        Assert.Equal(2003, parsed.DateOfBirth!.Value.Year);
    }

    [Theory]
    [InlineData("20001100112341", 1900, 1, 10)]
    [InlineData("29912310112341", 1999, 12, 31)]
    [InlineData("30001100112341", 2000, 1, 10)]
    [InlineData("39912310112341", 2099, 12, 31)]
    public void YearBoundaryDates_Parse(string nid, int year, int month, int day)
    {
        var parsed = EgyptianNationalIdValidator.Parse(nid);
        Assert.True(parsed.Valid);
        Assert.Equal(year, parsed.DateOfBirth!.Value.Year);
        Assert.Equal(month, parsed.DateOfBirth!.Value.Month);
        Assert.Equal(day, parsed.DateOfBirth!.Value.Day);
    }

    [Theory]
    [InlineData("20002290112345", false)]  // 1900 NOT a leap year (div 100, not 400)
    [InlineData("30002290112345", true)]   // 2000 IS a leap year (div 400)
    [InlineData("30402290112345", true)]   // 2004 — div 4, not 100
    [InlineData("31202290112345", true)]   // 2012
    [InlineData("30502290112345", false)]  // 2005 not leap
    public void LeapYearFebruary29_HandledCorrectly(string nid, bool valid)
    {
        Assert.Equal(valid, EgyptianNationalIdValidator.IsValid(nid));
    }

    [Theory]
    [InlineData("1", Gender.Male)]
    [InlineData("3", Gender.Male)]
    [InlineData("5", Gender.Male)]
    [InlineData("7", Gender.Male)]
    [InlineData("9", Gender.Male)]
    [InlineData("0", Gender.Female)]
    [InlineData("2", Gender.Female)]
    [InlineData("4", Gender.Female)]
    [InlineData("6", Gender.Female)]
    [InlineData("8", Gender.Female)]
    public void EveryGenderDigit_ResolvesCorrectGender(string serialPos13, Gender expected)
    {
        // Position 13 (1-indexed) = index 12; last char is unused trailing digit.
        var nid = "295041501123" + serialPos13 + "0";
        var parsed = EgyptianNationalIdValidator.Parse(nid);
        Assert.True(parsed.Valid);
        Assert.Equal(expected, parsed.Gender);
    }

    [Theory]
    [InlineData("0")]
    [InlineData("1")]
    [InlineData("4")]
    [InlineData("5")]
    [InlineData("6")]
    [InlineData("7")]
    [InlineData("8")]
    [InlineData("9")]
    public void UnsupportedCenturyDigits_Rejected(string centuryDigit)
    {
        Assert.False(EgyptianNationalIdValidator.IsValid(centuryDigit + "9504150112345"));
    }

    // ── Phone parametrics ──────────────────────────────────────────

    [Theory]
    [InlineData("010")]
    [InlineData("011")]
    [InlineData("012")]
    [InlineData("015")]
    public void EveryMobilePrefix_Normalises(string prefix)
    {
        var raw = prefix + "12345678";
        var expected = "+201" + prefix[2..] + "12345678";
        Assert.Equal(expected, EgyptianPhoneValidator.Normalise(raw));
    }

    [Theory]
    [InlineData("+201012345678")]
    [InlineData("  +201012345678  ")]
    [InlineData("+20 10 1234 5678")]
    [InlineData("+20-10-1234-5678")]
    [InlineData("0020-101-234-5678")]
    public void Phone_CanonicalFormAfterStrip(string raw)
    {
        Assert.Equal("+201012345678", EgyptianPhoneValidator.Normalise(raw));
    }

    [Theory]
    [InlineData("+201312345678")]
    [InlineData("+201712345678")]
    [InlineData("+201812345678")]
    [InlineData("+201912345678")]
    [InlineData("+201412345678")]
    [InlineData("+201612345678")]
    [InlineData("+20101234567")]
    [InlineData("+2010123456789")]
    [InlineData("+2010123456A8")]
    public void InvalidPhone_Rejected(string raw)
    {
        Assert.Null(EgyptianPhoneValidator.Normalise(raw));
        Assert.False(EgyptianPhoneValidator.IsValid(raw));
    }

    // ── IBAN parametrics ───────────────────────────────────────────

    [Theory]
    [InlineData("EG380019000500000000263180002")]
    [InlineData("eg380019000500000000263180002")]
    [InlineData("EG38 0019 0005 0000 0000 2631 8000 2")]
    [InlineData("  EG380019000500000000263180002  ")]
    public void EveryCanonicalIbanForm_Passes(string raw)
    {
        Assert.True(EgyptianIbanValidator.IsValid(raw));
    }

    [Theory]
    [InlineData("EG380019000500000000263180001")]   // perturb check digits
    [InlineData("EG380019000500000000263180004")]
    [InlineData("EG370019000500000000263180002")]
    [InlineData("EG3800190005000000002631800OO")]   // garbage tail
    [InlineData("EG3800190005O00000002631800O2")]   // letter-where-digit
    [InlineData("GB380019000500000000263180002")]   // not Egypt
    [InlineData("XX380019000500000000263180002")]   // bogus country
    [InlineData(" ")]
    [InlineData(" EG ")]
    public void IbanNegatives_Reject(string raw)
    {
        Assert.False(EgyptianIbanValidator.IsValid(raw));
    }
}
