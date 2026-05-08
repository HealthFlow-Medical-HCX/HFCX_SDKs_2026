// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Text.Json;
using HealthFlow.Hfcx.Sdk.Exceptions;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Hand-rolled validator for the Egyptian FHIR-IG profile rules that
/// matter for HFCX's reject-on-receipt behaviour. Sister to Java's
/// <c>FhirValidator</c> and Python's <c>FhirValidator</c>. Same accept /
/// reject decisions across all three SDKs.
/// </summary>
public sealed class FhirValidator
{
    /// <summary>System URI for the Egyptian National-ID identifier slice.</summary>
    public const string NationalIdSystem = "http://hcx-egypt.gov.eg/identifiers/national-id";

    /// <summary>Validate the Bundle JSON; throws on first violation.</summary>
    public void Validate(string? bundleJson)
    {
        if (string.IsNullOrWhiteSpace(bundleJson))
        {
            throw new BadFhirJsonException("FHIR Bundle payload is empty");
        }

        JsonDocument doc;
        try
        {
            doc = JsonDocument.Parse(bundleJson);
        }
        catch (JsonException ex)
        {
            throw new BadFhirJsonException(
                $"FHIR payload is not valid JSON: {ex.Message}");
        }

        using (doc)
        {
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                throw new NotABundleException(
                    $"Top-level FHIR resource must be an object (got {root.ValueKind})");
            }

            var resourceType = ReadString(root, "resourceType");
            if (!string.Equals(resourceType, "Bundle", StringComparison.Ordinal))
            {
                throw new NotABundleException(
                    $"Top-level resource must be Bundle (got '{resourceType ?? "<missing>"}')");
            }

            if (string.IsNullOrEmpty(ReadString(root, "type")))
            {
                throw new BundleMissingTypeException(
                    "Bundle.type is required by the Egyptian IG");
            }

            if (!root.TryGetProperty("entry", out var entries) || entries.ValueKind != JsonValueKind.Array)
            {
                return;
            }

            foreach (var wrapper in entries.EnumerateArray())
            {
                if (wrapper.ValueKind != JsonValueKind.Object)
                {
                    continue;
                }

                if (!wrapper.TryGetProperty("resource", out var resource)
                    || resource.ValueKind != JsonValueKind.Object)
                {
                    continue;
                }

                if (string.Equals(ReadString(resource, "resourceType"), "Patient", StringComparison.Ordinal))
                {
                    ValidatePatient(resource);
                }
            }
        }
    }

    private static void ValidatePatient(JsonElement patient)
    {
        var hasNationalId = false;
        if (patient.TryGetProperty("identifier", out var identifiers)
            && identifiers.ValueKind == JsonValueKind.Array)
        {
            foreach (var ident in identifiers.EnumerateArray())
            {
                if (ident.ValueKind != JsonValueKind.Object)
                {
                    continue;
                }

                if (string.Equals(ReadString(ident, "system"), NationalIdSystem, StringComparison.Ordinal))
                {
                    hasNationalId = true;
                    break;
                }
            }
        }

        if (!hasNationalId)
        {
            throw new PatientMissingNationalIdException(
                $"Patient is missing an identifier with system {NationalIdSystem}");
        }

        if (!patient.TryGetProperty("address", out var addresses)
            || addresses.ValueKind != JsonValueKind.Array
            || addresses.GetArrayLength() == 0)
        {
            throw new PatientNonEgyptianException(
                "Patient.address[0] is required by the Egyptian IG");
        }

        var first = addresses[0];
        var country = first.ValueKind == JsonValueKind.Object ? ReadString(first, "country") : null;
        if (!string.Equals(country, "EG", StringComparison.Ordinal))
        {
            throw new PatientNonEgyptianException(
                $"Patient.address[0].country must be 'EG' (got '{country ?? "<missing>"}')");
        }
    }

    private static string? ReadString(JsonElement element, string field)
    {
        if (element.ValueKind != JsonValueKind.Object)
        {
            return null;
        }

        if (!element.TryGetProperty(field, out var value))
        {
            return null;
        }

        return value.ValueKind == JsonValueKind.String ? value.GetString() : null;
    }
}
