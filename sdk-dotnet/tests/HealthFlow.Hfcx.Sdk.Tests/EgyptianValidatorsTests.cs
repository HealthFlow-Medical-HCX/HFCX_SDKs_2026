// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using HealthFlow.Hfcx.Sdk.Validators;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant: same input → same accept/reject decision as
/// Java's <c>EgyptianValidatorsTest</c> and Python's
/// <c>test_egyptian_validators.py</c>.
/// </summary>
public class EgyptianValidatorsTests
{
    // ── Governorate ────────────────────────────────────────────────

    [Fact]
    public void Governorate_HasExactly27Entries()
    {
        Assert.Equal(27, EgyptianGovernorate.All.Count);
    }

    [Theory]
    [InlineData("01", "CAIRO")]
    [InlineData("21", "GIZA")]
    [InlineData("35", "SOUTH_SINAI")]
    public void Governorate_FromCode_FindsKnownEntries(string code, string expected)
    {
        var g = EgyptianGovernorate.FromCode(code);
        Assert.NotNull(g);
        Assert.Equal(expected, g!.Name);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("00")]
    [InlineData("99")]
    [InlineData("XX")]
    public void Governorate_FromCode_RejectsUnknown(string? code)
    {
        Assert.Null(EgyptianGovernorate.FromCode(code));
    }

    // ── National ID ────────────────────────────────────────────────

    [Theory]
    [InlineData("29504150112345")]   // 1995-04-15, Cairo
    [InlineData("30312312112340")]   // 2003-12-31, Giza
    [InlineData("29902280298765")]   // 1999-02-28, Alexandria
    public void Nid_HappyPaths(string id)
    {
        Assert.True(EgyptianNationalIdValidator.IsValid(id));
    }

    [Fact]
    public void Nid_ParseExposesFields()
    {
        var r = EgyptianNationalIdValidator.Parse("29504150112345");
        Assert.True(r.Valid);
        Assert.NotNull(r.DateOfBirth);
        Assert.Equal(1995, r.DateOfBirth!.Value.Year);
        Assert.Equal(4, r.DateOfBirth!.Value.Month);
        Assert.Equal(15, r.DateOfBirth!.Value.Day);
        Assert.Same(EgyptianGovernorate.Cairo, r.Governorate);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("123")]
    [InlineData("295041501123450")]
    [InlineData("2950415011234")]
    public void Nid_RejectsWrongLength(string? id)
    {
        Assert.False(EgyptianNationalIdValidator.IsValid(id));
    }

    [Theory]
    [InlineData("2950415011234A")]
    [InlineData("29504X50112345")]
    public void Nid_RejectsNonDigits(string id)
    {
        Assert.False(EgyptianNationalIdValidator.IsValid(id));
    }

    [Theory]
    [InlineData("19504150112345")]
    [InlineData("49504150112345")]
    public void Nid_RejectsUnknownCenturyDigit(string id)
    {
        Assert.False(EgyptianNationalIdValidator.IsValid(id));
    }

    [Theory]
    [InlineData("29513320112345")]   // month 13
    [InlineData("29502310112345")]   // Feb 31
    [InlineData("29502290112345")]   // 1995 not a leap year
    [InlineData("29504310112345")]   // April 31
    public void Nid_RejectsImpossibleDate(string id)
    {
        Assert.False(EgyptianNationalIdValidator.IsValid(id));
    }

    [Theory]
    [InlineData("29504150512345")]  // gov 05
    [InlineData("29504159912345")]  // gov 99
    public void Nid_RejectsUnknownGovernorate(string id)
    {
        Assert.False(EgyptianNationalIdValidator.IsValid(id));
    }

    [Fact]
    public void Nid_GenderInferredFromSerialDigit()
    {
        // Position 13 (1-indexed) = index 12.
        var male = EgyptianNationalIdValidator.Parse("29504150112350");   // '5' odd
        var female = EgyptianNationalIdValidator.Parse("29504150112360"); // '6' even
        Assert.Equal(Gender.Male, male.Gender);
        Assert.Equal(Gender.Female, female.Gender);
    }

    // ── Phone ──────────────────────────────────────────────────────

    [Theory]
    [InlineData("+201012345678", "+201012345678")]
    [InlineData("00201012345678", "+201012345678")]
    [InlineData("201012345678", "+201012345678")]
    [InlineData("01012345678", "+201012345678")]
    public void Phone_NormalisesCanonicalForms(string raw, string expected)
    {
        Assert.Equal(expected, EgyptianPhoneValidator.Normalise(raw));
    }

    [Theory]
    [InlineData("01012345678")]
    [InlineData("01112345678")]
    [InlineData("01212345678")]
    [InlineData("01512345678")]
    public void Phone_AcceptsAllFourMobilePrefixes(string raw)
    {
        Assert.True(EgyptianPhoneValidator.IsValid(raw));
    }

    [Theory]
    [InlineData("+20 10 1234 5678")]
    [InlineData("0101-234-5678")]
    public void Phone_StripsWhitespaceAndHyphens(string raw)
    {
        Assert.Equal("+201012345678", EgyptianPhoneValidator.Normalise(raw));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("01312345678")]   // 013 not a mobile prefix
    [InlineData("0101234567")]    // 10 digits, too short
    [InlineData("010123456789")]  // 12 digits, too long
    [InlineData("11012345678")]
    [InlineData("+30101234567")]  // wrong country
    public void Phone_RejectsBadInputs(string? raw)
    {
        Assert.False(EgyptianPhoneValidator.IsValid(raw));
    }

    // ── IBAN ───────────────────────────────────────────────────────

    [Fact]
    public void Iban_AcceptsCbeExample()
    {
        Assert.True(EgyptianIbanValidator.IsValid("EG380019000500000000263180002"));
    }

    [Fact]
    public void Iban_IsCaseInsensitiveAndStripsSpaces()
    {
        Assert.True(EgyptianIbanValidator.IsValid("eg38 0019 0005 0000 0000 2631 8000 2"));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("EG380019000500000000263180003")]   // bad check digits
    [InlineData("FR380019000500000000263180002")]   // wrong country
    [InlineData("EG3800190005000000002631800022")]  // 30 chars
    [InlineData("EG380019000500000000263180")]      // 26 chars
    [InlineData("EG3800190005000000002631800!2")]   // bad char
    public void Iban_RejectsBadInputs(string? raw)
    {
        Assert.False(EgyptianIbanValidator.IsValid(raw));
    }
}
