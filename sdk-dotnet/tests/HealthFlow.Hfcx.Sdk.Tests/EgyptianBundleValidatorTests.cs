// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Recipient;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant: identical Bundle JSON → identical accept/reject
/// decision as Java's <c>EgyptianBundleValidator</c> and Python's
/// <c>EgyptianBundleValidator</c>.
/// </summary>
public class EgyptianBundleValidatorTests
{
    private const string Nid = FhirValidator.NationalIdSystem;

    private static string PatientWithNid(string nid) =>
        $$"""
        {"resourceType":"Patient",
         "identifier":[{"system":"{{Nid}}","value":"{{nid}}"}],
         "address":[{"country":"EG"}]}
        """;

    private static string PatientWithPhone(string phone) =>
        $$"""
        {"resourceType":"Patient",
         "identifier":[{"system":"{{Nid}}","value":"29504150112345"}],
         "address":[{"country":"EG"}],
         "telecom":[{"system":"phone","value":"{{phone}}"}]}
        """;

    private static string OrgWithIban(string ibanValue, string system = "https://example.org/iban") =>
        $$"""
        {"resourceType":"Organization","name":"PayerCo",
         "identifier":[{"system":"{{system}}","value":"{{ibanValue}}"}]}
        """;

    private static string Bundle(string resource) =>
        $$"""
        {"resourceType":"Bundle","type":"collection","entry":[{"resource":{{resource}}}]}
        """;

    // ── happy paths ────────────────────────────────────────────────

    [Fact]
    public void ValidPatient_Passes()
    {
        new EgyptianBundleValidator().Validate(Bundle(PatientWithNid("29504150112345")));
    }

    [Fact]
    public void ValidPatientWithPhone_Passes()
    {
        new EgyptianBundleValidator().Validate(Bundle(PatientWithPhone("+201012345678")));
    }

    [Fact]
    public void ValidOrganisationIban_Passes()
    {
        new EgyptianBundleValidator().Validate(Bundle(OrgWithIban("EG380019000500000000263180002")));
    }

    [Fact]
    public void EmptyPayload_ReturnsSilently()
    {
        new EgyptianBundleValidator().Validate("");
        new EgyptianBundleValidator().Validate("   ");
        new EgyptianBundleValidator().Validate(null);
    }

    [Fact]
    public void MalformedJson_ReturnsSilently()
    {
        new EgyptianBundleValidator().Validate("{not valid");
    }

    [Fact]
    public void NonObjectRoot_ReturnsSilently()
    {
        new EgyptianBundleValidator().Validate("\"hello\"");
        new EgyptianBundleValidator().Validate("[]");
    }

    [Fact]
    public void NoEntryArray_ReturnsSilently()
    {
        new EgyptianBundleValidator().Validate("""{"resourceType":"Bundle","type":"collection"}""");
    }

    [Fact]
    public void UnknownResourceTypes_Skipped()
    {
        new EgyptianBundleValidator().Validate(
            Bundle("""{"resourceType":"Practitioner","id":"p1"}"""));
    }

    // ── National-ID failures ───────────────────────────────────────

    [Fact]
    public void InvalidNationalIdValue_Raises()
    {
        var ex = Assert.Throws<NationalIdInvalidException>(
            () => new EgyptianBundleValidator().Validate(Bundle(PatientWithNid("not-a-real-nid"))));
        Assert.Contains("not-a-real-nid", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void NidWrongLength_Raises()
    {
        Assert.Throws<NationalIdInvalidException>(
            () => new EgyptianBundleValidator().Validate(Bundle(PatientWithNid("1234"))));
    }

    [Fact]
    public void NidUnknownGovernorate_Raises()
    {
        Assert.Throws<NationalIdInvalidException>(
            () => new EgyptianBundleValidator().Validate(Bundle(PatientWithNid("29504150999991"))));
    }

    [Fact]
    public void NidImpossibleDate_Raises()
    {
        Assert.Throws<NationalIdInvalidException>(
            () => new EgyptianBundleValidator().Validate(Bundle(PatientWithNid("29513320112345"))));
    }

    [Fact]
    public void PatientWithoutNidIdentifier_PassesAtThisLayer()
    {
        // Missing-NID is FhirValidator's job; if FhirValidator is disabled
        // but this layer is enabled, we don't synthesise an error.
        const string patient = """
            {"resourceType":"Patient","address":[{"country":"EG"}]}
            """;
        new EgyptianBundleValidator().Validate(Bundle(patient));
    }

    [Fact]
    public void OtherIdentifierSystem_Ignored()
    {
        const string patient = """
            {"resourceType":"Patient",
             "identifier":[{"system":"http://example.org/passport","value":"anything"}],
             "address":[{"country":"EG"}]}
            """;
        new EgyptianBundleValidator().Validate(Bundle(patient));
    }

    // ── Phone failures ─────────────────────────────────────────────

    [Fact]
    public void InvalidPhoneValue_Raises()
    {
        var ex = Assert.Throws<PhoneInvalidException>(
            () => new EgyptianBundleValidator().Validate(Bundle(PatientWithPhone("01312345678"))));
        Assert.Contains("01312345678", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void PhoneWrongCountryCode_Raises()
    {
        Assert.Throws<PhoneInvalidException>(
            () => new EgyptianBundleValidator().Validate(Bundle(PatientWithPhone("+30101234567"))));
    }

    [Fact]
    public void TelecomOtherSystems_Ignored()
    {
        const string patient = """
            {"resourceType":"Patient",
             "identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"29504150112345"}],
             "address":[{"country":"EG"}],
             "telecom":[{"system":"email","value":"x@example.com"},{"system":"fax","value":"0211111111"}]}
            """;
        new EgyptianBundleValidator().Validate(Bundle(patient));
    }

    // ── IBAN failures ──────────────────────────────────────────────

    [Fact]
    public void InvalidIbanValue_Raises()
    {
        var ex = Assert.Throws<IbanInvalidException>(
            () => new EgyptianBundleValidator().Validate(Bundle(OrgWithIban("EG380019000500000000263180003"))));
        Assert.Contains("EG38", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void IbanWrongCountry_Raises()
    {
        Assert.Throws<IbanInvalidException>(
            () => new EgyptianBundleValidator().Validate(Bundle(OrgWithIban("FR380019000500000000263180002"))));
    }

    [Fact]
    public void OrgIdentifierWithoutIbanSystem_Ignored()
    {
        new EgyptianBundleValidator().Validate(
            Bundle(OrgWithIban("garbage-value", system: "http://example.org/tax-id")));
    }

    [Fact]
    public void OrganisationWithoutIdentifiers_Ignored()
    {
        new EgyptianBundleValidator().Validate(
            Bundle("""{"resourceType":"Organization","name":"PayerCo"}"""));
    }

    // ── Multi-resource walks ───────────────────────────────────────

    [Fact]
    public void FirstInvalidResource_ShortCircuits()
    {
        const string goodOrg = """
            {"resourceType":"Organization","name":"PayerCo",
             "identifier":[{"system":"https://example.org/iban","value":"EG380019000500000000263180002"}]}
            """;
        var badPatient = PatientWithPhone("01312345678");
        var bundle = $$"""
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{{goodOrg}}},{"resource":{{badPatient}}}
            ]}
            """;
        Assert.Throws<PhoneInvalidException>(
            () => new EgyptianBundleValidator().Validate(bundle));
    }

    [Fact]
    public void MultipleOrganisations_EachIbanChecked()
    {
        var goodOrg = OrgWithIban("EG380019000500000000263180002");
        var badOrg = OrgWithIban("EG380019000500000000263180003");
        var bundle = $$"""
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{{goodOrg}}},{"resource":{{badOrg}}}
            ]}
            """;
        Assert.Throws<IbanInvalidException>(
            () => new EgyptianBundleValidator().Validate(bundle));
    }
}
