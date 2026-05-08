// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Collections.Generic;

namespace HealthFlow.Hfcx.Sdk.Client;

/// <summary>
/// Closed set of outbound operations the SDK supports. Cross-SDK
/// invariant with Java's <c>Operation</c> and Python's
/// <c>Operation</c> enum.
/// </summary>
public enum Operation
{
    CheckEligibility,
    SubmitPreauth,
    SubmitClaim,
    SendCommunication,
    NotifyPayment,
}

/// <summary>
/// Default endpoint paths per Integration Guide §22. Override via
/// <see cref="HfcxClient"/>'s constructor.
/// </summary>
public static class DefaultEndpoints
{
    /// <summary>Per-operation default path under the gateway URL.</summary>
    public static readonly IReadOnlyDictionary<Operation, string> Map =
        new Dictionary<Operation, string>
        {
            [Operation.CheckEligibility] = "/v1/coverageeligibility/check",
            [Operation.SubmitPreauth] = "/v1/preauth/submit",
            [Operation.SubmitClaim] = "/v1/claim/submit",
            [Operation.SendCommunication] = "/v1/communication/on_request",
            [Operation.NotifyPayment] = "/v1/paymentnotice/notify",
        };
}
