// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Generic;
using System.Text.Json;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Recipient;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant: same Bundle JSON → same accept/reject decision
/// as Java's <c>FhirValidator</c> and Python's <c>FhirValidator</c>.
/// Sister to <c>test_fhir_validator.py</c>.
/// </summary>
public class FhirValidatorTests
{
    private const string NationalIdSystem = FhirValidator.NationalIdSystem;

    private static string PatientJson(
        string? nid = "29504150112345",
        string? country = "EG",
        IReadOnlyDictionary<string, string>? extraIdentifier = null,
        bool skipAddress = false)
    {
        var idents = new List<string>();
        if (nid is not null)
        {
            idents.Add($"{{\"system\":\"{NationalIdSystem}\",\"value\":\"{nid}\"}}");
        }

        if (extraIdentifier is not null)
        {
            var sys = extraIdentifier["system"];
            var val = extraIdentifier["value"];
            idents.Add($"{{\"system\":\"{sys}\",\"value\":\"{val}\"}}");
        }

        var identsJson = "[" + string.Join(",", idents) + "]";
        var addressJson = skipAddress
            ? string.Empty
            : country is null
                ? ",\"address\":[{}]"
                : $",\"address\":[{{\"country\":\"{country}\"}}]";
        return "{\"resourceType\":\"Patient\",\"identifier\":" + identsJson + addressJson + "}";
    }

    private static string BundleOf(params string[] resources)
    {
        var entries = new List<string>();
        foreach (var r in resources)
        {
            entries.Add($"{{\"resource\":{r}}}");
        }

        return "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[" + string.Join(",", entries) + "]}";
    }

    [Fact]
    public void Valid_BundleWithEgyptianPatient_Passes()
    {
        new FhirValidator().Validate(BundleOf(PatientJson()));
    }

    [Fact]
    public void Valid_BundleWithoutPatient_Passes()
    {
        new FhirValidator().Validate(BundleOf("""{"resourceType":"Organization","name":"PayerCo"}"""));
    }

    [Fact]
    public void EmptyEntryArray_Passes()
    {
        new FhirValidator().Validate("""{"resourceType":"Bundle","type":"collection","entry":[]}""");
    }

    [Fact]
    public void MissingEntryKey_Passes()
    {
        new FhirValidator().Validate("""{"resourceType":"Bundle","type":"collection"}""");
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("\t\n")]
    public void BlankPayload_RaisesBadFhirJson(string body)
    {
        Assert.Throws<BadFhirJsonException>(() => new FhirValidator().Validate(body));
    }

    [Fact]
    public void MalformedJson_RaisesBadFhirJson()
    {
        Assert.Throws<BadFhirJsonException>(
            () => new FhirValidator().Validate("{not: valid json}"));
    }

    [Fact]
    public void TopLevelArray_RaisesNotABundle()
    {
        Assert.Throws<NotABundleException>(
            () => new FhirValidator().Validate("""[{"resourceType":"Bundle"}]"""));
    }

    [Fact]
    public void TopLevelString_RaisesNotABundle()
    {
        Assert.Throws<NotABundleException>(
            () => new FhirValidator().Validate("\"Bundle\""));
    }

    [Fact]
    public void WrongResourceType_RaisesNotABundle()
    {
        var ex = Assert.Throws<NotABundleException>(
            () => new FhirValidator().Validate("""{"resourceType":"Patient","type":"collection"}"""));
        Assert.Contains("Patient", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void MissingResourceType_RaisesNotABundle()
    {
        Assert.Throws<NotABundleException>(
            () => new FhirValidator().Validate("""{"type":"collection"}"""));
    }

    [Fact]
    public void MissingBundleType_RaisesBundleMissingType()
    {
        Assert.Throws<BundleMissingTypeException>(
            () => new FhirValidator().Validate("""{"resourceType":"Bundle"}"""));
    }

    [Fact]
    public void EmptyBundleType_RaisesBundleMissingType()
    {
        Assert.Throws<BundleMissingTypeException>(
            () => new FhirValidator().Validate("""{"resourceType":"Bundle","type":""}"""));
    }

    [Fact]
    public void PatientWithNoIdentifierArray_Raises()
    {
        var bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{"resourceType":"Patient","address":[{"country":"EG"}]}}
            ]}
            """;
        Assert.Throws<PatientMissingNationalIdException>(
            () => new FhirValidator().Validate(bundle));
    }

    [Fact]
    public void PatientWithOnlyOtherIdentifierSystem_Raises()
    {
        var extra = new Dictionary<string, string>
        {
            ["system"] = "http://example.org/passport",
            ["value"] = "P12345",
        };
        Assert.Throws<PatientMissingNationalIdException>(
            () => new FhirValidator().Validate(BundleOf(PatientJson(nid: null, extraIdentifier: extra))));
    }

    [Fact]
    public void PatientWithEmptyIdentifierArray_Raises()
    {
        Assert.Throws<PatientMissingNationalIdException>(
            () => new FhirValidator().Validate(BundleOf(PatientJson(nid: null))));
    }

    [Fact]
    public void PatientWithoutAddress_Raises()
    {
        Assert.Throws<PatientNonEgyptianException>(
            () => new FhirValidator().Validate(BundleOf(PatientJson(skipAddress: true))));
    }

    [Fact]
    public void PatientWithEmptyAddressArray_Raises()
    {
        var bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{
                "resourceType":"Patient",
                "identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"29504150112345"}],
                "address":[]
              }}
            ]}
            """;
        Assert.Throws<PatientNonEgyptianException>(
            () => new FhirValidator().Validate(bundle));
    }

    [Fact]
    public void PatientWithNonEgCountry_Raises()
    {
        var ex = Assert.Throws<PatientNonEgyptianException>(
            () => new FhirValidator().Validate(BundleOf(PatientJson(country: "US"))));
        Assert.Contains("US", ex.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void PatientWithMissingCountry_Raises()
    {
        Assert.Throws<PatientNonEgyptianException>(
            () => new FhirValidator().Validate(BundleOf(PatientJson(country: null))));
    }

    [Fact]
    public void OnlyAddressZero_IsCheckedForCountry()
    {
        var bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{
                "resourceType":"Patient",
                "identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"29504150112345"}],
                "address":[{"country":"US"},{"country":"EG"}]
              }}
            ]}
            """;
        Assert.Throws<PatientNonEgyptianException>(
            () => new FhirValidator().Validate(bundle));
    }

    [Fact]
    public void MultiplePatients_FirstFailureShortCircuits()
    {
        var bad = PatientJson(nid: null);
        var good = PatientJson();
        Assert.Throws<PatientMissingNationalIdException>(
            () => new FhirValidator().Validate(BundleOf(bad, good)));
    }

    [Fact]
    public void NonPatientResources_SkippedSilently()
    {
        const string practitioner = """{"resourceType":"Practitioner","id":"p-1"}""";
        const string organisation = """{"resourceType":"Organization","name":"PayerCo"}""";
        new FhirValidator().Validate(BundleOf(practitioner, organisation, PatientJson()));
    }

    [Fact]
    public void EntryWrapperWithoutResource_Skipped()
    {
        var bundle = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {},{"fullUrl":"urn:uuid:abc"},{"resource":{"resourceType":"Patient","identifier":[{"system":"http://hcx-egypt.gov.eg/identifiers/national-id","value":"29504150112345"}],"address":[{"country":"EG"}]}}
            ]}
            """;
        new FhirValidator().Validate(bundle);
    }

    [Fact]
    public void NonArrayEntryValue_IsTolerated()
    {
        new FhirValidator().Validate("""{"resourceType":"Bundle","type":"collection","entry":42}""");
    }

    [Fact]
    public void NationalIdValue_NotValidatedAtThisLayer()
    {
        // FhirValidator only asserts the slice exists; value-level
        // validity is EgyptianBundleValidator's job.
        new FhirValidator().Validate(BundleOf(PatientJson(nid: "not-a-real-nid")));
    }

    [Fact]
    public void NationalIdSystem_PinnedConstant()
    {
        Assert.Equal("http://hcx-egypt.gov.eg/identifiers/national-id", FhirValidator.NationalIdSystem);
    }
}
