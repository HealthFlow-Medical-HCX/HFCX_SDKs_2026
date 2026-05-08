// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

namespace HealthFlow.Hfcx.Sdk.Client;

/// <summary>
/// Common return shape for every <see cref="HfcxClient"/> sender method.
/// Sister to Java's <c>HfcxResponse</c> record and Python's
/// <c>HfcxResponse</c> dataclass.
/// </summary>
public sealed record HfcxResponse(string CorrelationId, Status Status);
