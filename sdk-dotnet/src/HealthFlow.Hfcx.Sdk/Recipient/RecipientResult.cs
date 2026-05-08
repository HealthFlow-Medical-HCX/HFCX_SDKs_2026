// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Collections.Generic;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Outcome of a successful <see cref="RecipientHandler.Handle"/> call.
/// Sister to Java's <c>RecipientResult</c> record and Python's
/// <c>RecipientResult</c> dataclass.
/// </summary>
public sealed record RecipientResult(
    string DecryptedPayload,
    IReadOnlyDictionary<string, string> ProtocolHeaders,
    string CorrelationId);
