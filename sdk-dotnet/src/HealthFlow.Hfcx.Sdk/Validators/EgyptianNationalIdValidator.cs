// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Globalization;

namespace HealthFlow.Hfcx.Sdk.Validators;

/// <summary>
/// Gender encoded in the National ID's serial digit (odd = male, even = female).
/// </summary>
public enum Gender
{
    Male,
    Female,
}

/// <summary>Rich validation result for an Egyptian National ID.</summary>
public sealed record NationalIdResult(
    bool Valid,
    string Reason,
    DateOnly? DateOfBirth = null,
    EgyptianGovernorate? Governorate = null,
    Gender? Gender = null);

/// <summary>
/// Structural validator for Egyptian National-ID numbers. Sister to the
/// Java SDK's <c>EgyptianNationalIDValidator</c> and the Python SDK's
/// <c>egyptian_national_id</c>. Cross-SDK invariant: same input → same
/// accept / reject decision.
/// </summary>
public static class EgyptianNationalIdValidator
{
    /// <summary>Returns <see langword="true"/> iff the ID is structurally valid.</summary>
    public static bool IsValid(string? nationalId) => Parse(nationalId).Valid;

    /// <summary>Decode a National ID into a <see cref="NationalIdResult"/>.</summary>
    public static NationalIdResult Parse(string? nationalId)
    {
        if (nationalId is null || nationalId.Length != 14)
        {
            return new NationalIdResult(false, "must be exactly 14 digits");
        }

        for (var i = 0; i < nationalId.Length; i++)
        {
            if (!char.IsDigit(nationalId[i]))
            {
                return new NationalIdResult(false, "contains a non-digit character");
            }
        }

        var centuryDigit = nationalId[0];
        int yearPrefix = centuryDigit switch
        {
            '2' => 1900,
            '3' => 2000,
            _ => -1,
        };
        if (yearPrefix < 0)
        {
            return new NationalIdResult(
                false,
                $"century digit must be 2 or 3 (got '{centuryDigit}')");
        }

        var year = yearPrefix + int.Parse(nationalId.AsSpan(1, 2), CultureInfo.InvariantCulture);
        var month = int.Parse(nationalId.AsSpan(3, 2), CultureInfo.InvariantCulture);
        var day = int.Parse(nationalId.AsSpan(5, 2), CultureInfo.InvariantCulture);

        DateOnly dob;
        try
        {
            dob = new DateOnly(year, month, day);
        }
        catch (ArgumentOutOfRangeException)
        {
            return new NationalIdResult(
                false,
                $"date of birth {year}-{month}-{day} is not a real Gregorian date");
        }

        var govCode = nationalId.Substring(7, 2);
        var governorate = EgyptianGovernorate.FromCode(govCode);
        if (governorate is null)
        {
            return new NationalIdResult(
                false,
                $"governorate code '{govCode}' is not recognised");
        }

        // Position 13 (1-indexed) is the gender digit.
        var genderDigit = nationalId[12] - '0';
        var gender = (genderDigit % 2 == 1) ? Gender.Male : Gender.Female;

        return new NationalIdResult(
            Valid: true,
            Reason: "ok",
            DateOfBirth: dob,
            Governorate: governorate,
            Gender: gender);
    }
}
