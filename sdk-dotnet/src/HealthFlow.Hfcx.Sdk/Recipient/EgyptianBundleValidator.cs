// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Text.Json;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Validators;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Walks a FHIR Bundle and runs the Egyptian field-level validators
/// (National ID, phone, IBAN). Sister to Java's
/// <c>EgyptianBundleValidator</c> and Python's
/// <c>EgyptianBundleValidator</c>.
/// </summary>
public sealed class EgyptianBundleValidator
{
    /// <summary>Validate the Bundle JSON; throws on first violation.</summary>
    public void Validate(string? bundleJson)
    {
        if (string.IsNullOrWhiteSpace(bundleJson))
        {
            return;
        }

        JsonDocument doc;
        try
        {
            doc = JsonDocument.Parse(bundleJson);
        }
        catch (JsonException)
        {
            // FhirValidator runs first; fail-secure no-op here.
            return;
        }

        using (doc)
        {
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object) return;
            if (!root.TryGetProperty("entry", out var entries)
                || entries.ValueKind != JsonValueKind.Array)
            {
                return;
            }

            foreach (var wrapper in entries.EnumerateArray())
            {
                if (wrapper.ValueKind != JsonValueKind.Object) continue;
                if (!wrapper.TryGetProperty("resource", out var resource)
                    || resource.ValueKind != JsonValueKind.Object)
                {
                    continue;
                }

                var rt = ReadString(resource, "resourceType");
                if (string.Equals(rt, "Patient", StringComparison.Ordinal))
                {
                    ValidatePatient(resource);
                }
                else if (string.Equals(rt, "Organization", StringComparison.Ordinal))
                {
                    ValidateOrganization(resource);
                }
            }
        }
    }

    private static void ValidatePatient(JsonElement patient)
    {
        if (patient.TryGetProperty("identifier", out var identifiers)
            && identifiers.ValueKind == JsonValueKind.Array)
        {
            foreach (var ident in identifiers.EnumerateArray())
            {
                if (ident.ValueKind != JsonValueKind.Object) continue;
                if (string.Equals(ReadString(ident, "system"), FhirValidator.NationalIdSystem, StringComparison.Ordinal))
                {
                    var value = ReadString(ident, "value");
                    if (!EgyptianNationalIdValidator.IsValid(value))
                    {
                        throw new NationalIdInvalidException(
                            $"Patient National-ID identifier value '{value}' is not a valid Egyptian National ID");
                    }
                }
            }
        }

        if (patient.TryGetProperty("telecom", out var telecom)
            && telecom.ValueKind == JsonValueKind.Array)
        {
            foreach (var contact in telecom.EnumerateArray())
            {
                if (contact.ValueKind != JsonValueKind.Object) continue;
                if (string.Equals(ReadString(contact, "system"), "phone", StringComparison.Ordinal))
                {
                    var value = ReadString(contact, "value");
                    if (!EgyptianPhoneValidator.IsValid(value))
                    {
                        throw new PhoneInvalidException(
                            $"Patient phone '{value}' is not a valid Egyptian mobile number");
                    }
                }
            }
        }
    }

    private static void ValidateOrganization(JsonElement org)
    {
        if (!org.TryGetProperty("identifier", out var identifiers)
            || identifiers.ValueKind != JsonValueKind.Array)
        {
            return;
        }

        foreach (var ident in identifiers.EnumerateArray())
        {
            if (ident.ValueKind != JsonValueKind.Object) continue;
            var system = ReadString(ident, "system");
            if (system is not null
                && system.Contains("iban", StringComparison.Ordinal))
            {
                var value = ReadString(ident, "value");
                if (!EgyptianIbanValidator.IsValid(value))
                {
                    throw new IbanInvalidException(
                        $"Organization IBAN '{value}' is not a valid Egyptian IBAN");
                }
            }
        }
    }

    private static string? ReadString(JsonElement element, string field)
    {
        if (element.ValueKind != JsonValueKind.Object) return null;
        if (!element.TryGetProperty(field, out var value)) return null;
        return value.ValueKind == JsonValueKind.String ? value.GetString() : null;
    }
}
