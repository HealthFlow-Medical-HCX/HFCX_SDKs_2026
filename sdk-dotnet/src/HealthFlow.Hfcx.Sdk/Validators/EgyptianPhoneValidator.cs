// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Text.RegularExpressions;

namespace HealthFlow.Hfcx.Sdk.Validators;

/// <summary>
/// Validator and normaliser for Egyptian mobile phone numbers. Cross-SDK
/// invariant with Java's <c>EgyptianPhoneValidator</c> and Python's
/// <c>egyptian_phone</c>: accepts the four canonical mobile forms
/// (international <c>+</c>, double-zero, bare country code, local
/// 0-prefixed) and returns the <c>+201XXXXXXXXX</c> canonical form.
/// Mobile prefixes are 010, 011, 012, 015 (Vodafone, Etisalat, Orange,
/// WE).
/// </summary>
public static partial class EgyptianPhoneValidator
{
    /// <summary>Returns <see langword="true"/> iff the phone is a valid Egyptian mobile.</summary>
    public static bool IsValid(string? phone) => Normalise(phone) is not null;

    /// <summary>Return the canonical <c>+201XXXXXXXXX</c> form, or <see langword="null"/>.</summary>
    public static string? Normalise(string? phone)
    {
        if (phone is null)
        {
            return null;
        }

        var trimmed = phone.Trim().Replace(" ", "", StringComparison.Ordinal).Replace("-", "", StringComparison.Ordinal);
        string candidate;
        if (trimmed.StartsWith("+20", StringComparison.Ordinal))
        {
            candidate = trimmed;
        }
        else if (trimmed.StartsWith("0020", StringComparison.Ordinal))
        {
            candidate = "+20" + trimmed[4..];
        }
        else if (trimmed.StartsWith("20", StringComparison.Ordinal) && trimmed.Length == 12)
        {
            candidate = "+" + trimmed;
        }
        else if (trimmed.StartsWith('0') && trimmed.Length == 11)
        {
            candidate = "+20" + trimmed[1..];
        }
        else
        {
            return null;
        }

        return Normalised().IsMatch(candidate) ? candidate : null;
    }

    [GeneratedRegex(@"^\+201[0125]\d{8}$")]
    private static partial Regex Normalised();
}
