// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

namespace HealthFlow.Hfcx.Sdk.Client;

/// <summary>
/// Sealed marker for the five HFCX outbound request types. Sister to
/// Java's sealed <c>HfcxRequest</c> interface and Python's
/// <c>HfcxRequest</c> Protocol union.
/// </summary>
public interface IHfcxRequest
{
    /// <summary>Recipient participant code, e.g. <c>"payerco@hcx-egypt"</c>.</summary>
    string RecipientCode { get; }

    /// <summary>FHIR R4 Bundle JSON to encrypt and send.</summary>
    string PayloadBundle { get; }

    /// <summary>
    /// Caller-supplied correlation ID, or <see langword="null"/> to let
    /// the SDK generate one. Cross-SDK invariant: same MDC key
    /// (<c>"correlation_id"</c>) used by Java + Python.
    /// </summary>
    string? CorrelationId { get; }

    /// <summary>The HFCX operation this request is dispatched as.</summary>
    Operation Operation { get; }
}

/// <summary>Outbound coverage-eligibility check.</summary>
public sealed record CheckEligibilityRequest(
    string RecipientCode,
    string EligibilityBundle,
    string? CorrelationId = null) : IHfcxRequest
{
    string IHfcxRequest.PayloadBundle => EligibilityBundle;

    Operation IHfcxRequest.Operation => Operation.CheckEligibility;
}

/// <summary>Outbound preauthorisation submission.</summary>
public sealed record SubmitPreauthRequest(
    string RecipientCode,
    string PreauthBundle,
    string? CorrelationId = null) : IHfcxRequest
{
    string IHfcxRequest.PayloadBundle => PreauthBundle;

    Operation IHfcxRequest.Operation => Operation.SubmitPreauth;
}

/// <summary>Outbound claim submission.</summary>
public sealed record SubmitClaimRequest(
    string RecipientCode,
    string ClaimBundle,
    string? CorrelationId = null) : IHfcxRequest
{
    string IHfcxRequest.PayloadBundle => ClaimBundle;

    Operation IHfcxRequest.Operation => Operation.SubmitClaim;
}

/// <summary>Outbound communication request.</summary>
public sealed record SendCommunicationRequest(
    string RecipientCode,
    string CommunicationBundle,
    string? CorrelationId = null) : IHfcxRequest
{
    string IHfcxRequest.PayloadBundle => CommunicationBundle;

    Operation IHfcxRequest.Operation => Operation.SendCommunication;
}

/// <summary>Outbound payment-notice notification.</summary>
public sealed record NotifyPaymentRequest(
    string RecipientCode,
    string PaymentNoticeBundle,
    string? CorrelationId = null) : IHfcxRequest
{
    string IHfcxRequest.PayloadBundle => PaymentNoticeBundle;

    Operation IHfcxRequest.Operation => Operation.NotifyPayment;
}
