// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Security.Cryptography;

namespace HealthFlow.Hfcx.Sdk.Registry;

/// <summary>
/// Cached lookup result for a single HFCX participant.
/// </summary>
/// <remarks>
/// Sister to the Java SDK's <c>ParticipantCert</c> record and the Python
/// SDK's <c>ParticipantCert</c> dataclass. Cross-SDK invariant: the
/// fields and their semantics line up byte-for-byte across SDKs.
/// </remarks>
/// <param name="ParticipantCode">
/// HFCX participant code, e.g. <c>"payerco@hcx-egypt"</c>.
/// </param>
/// <param name="PublicKey">
/// RSA public key extracted from the participant's encryption cert.
/// Owned by the resolver — callers must not <c>Dispose()</c> it.
/// </param>
/// <param name="NotAfter">
/// Cert <c>notAfter</c> value (UTC). Cache TTL is set to
/// <c>NotAfter - PreExpiryBuffer</c> so a request never goes out with a
/// key the gateway is about to reject as expired.
/// </param>
public sealed record ParticipantCert(
    string ParticipantCode,
    RSA PublicKey,
    DateTimeOffset NotAfter);
