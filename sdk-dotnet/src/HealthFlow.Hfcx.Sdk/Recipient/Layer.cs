// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Pipeline layers in <see cref="RecipientHandler"/>, each independently
/// toggleable. Cross-SDK invariant with Java's <c>Layer</c> and Python's
/// <c>Layer</c> enum: 4 values in this exact order.
/// </summary>
public enum Layer
{
    /// <summary>Bearer-token validation.</summary>
    Bearer,

    /// <summary>Five protocol-header presence + format checks.</summary>
    Headers,

    /// <summary>FHIR-Bundle Egyptian-IG profile validation.</summary>
    Fhir,

    /// <summary>Egyptian field-value walks (NID / phone / IBAN).</summary>
    Egyptian,
}
