// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Numerics;
using System.Text;
using System.Text.RegularExpressions;

namespace HealthFlow.Hfcx.Sdk.Validators;

/// <summary>
/// Validator for Egyptian IBANs. Cross-SDK invariant with Java's
/// <c>EgyptianIBANValidator</c> and Python's <c>egyptian_iban</c>:
/// 29-char structural shape (<c>EG</c> + 2 check digits + 25
/// alphanumeric) plus the ISO 13616 mod-97 check.
/// </summary>
public static partial class EgyptianIbanValidator
{
    /// <summary>Returns <see langword="true"/> iff the IBAN is structurally valid and mod-97 = 1.</summary>
    public static bool IsValid(string? iban)
    {
        if (string.IsNullOrEmpty(iban))
        {
            return false;
        }

        var stripped = iban.Replace(" ", "", StringComparison.Ordinal).ToUpperInvariant();
        if (!Structure().IsMatch(stripped))
        {
            return false;
        }

        return Mod97(stripped) == 1;
    }

    private static int Mod97(string iban)
    {
        // Move the first four characters (country + check digits) to the end,
        // then convert each letter to two digits (A=10, B=11, ..., Z=35) and
        // compute mod 97 over the resulting numeric string.
        var rearranged = iban[4..] + iban[..4];
        var sb = new StringBuilder(rearranged.Length * 2);
        foreach (var c in rearranged)
        {
            if (c >= '0' && c <= '9')
            {
                sb.Append(c);
            }
            else
            {
                sb.Append((c - 'A' + 10).ToString(System.Globalization.CultureInfo.InvariantCulture));
            }
        }

        var big = BigInteger.Parse(sb.ToString(), System.Globalization.CultureInfo.InvariantCulture);
        return (int)(big % 97);
    }

    [GeneratedRegex(@"^EG\d{2}[A-Z0-9]{25}$")]
    private static partial Regex Structure();
}
