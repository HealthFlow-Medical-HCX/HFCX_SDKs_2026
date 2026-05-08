// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Threading;

namespace HealthFlow.Hfcx.Sdk.Logging;

/// <summary>
/// Cross-SDK-invariant correlation-ID propagation. Sister to Java's MDC
/// <c>correlationId</c> key and Python's
/// <c>hfcx_sdk._logging.correlation_id_scope</c> ContextVar.
/// </summary>
/// <remarks>
/// Backed by <see cref="AsyncLocal{T}"/> so the value flows across
/// <c>await</c> boundaries (the .NET equivalent of Python's ContextVar
/// behaviour). The MDC key name <c>"correlation_id"</c> is shared with
/// the other SDKs.
/// </remarks>
public static class CorrelationId
{
    /// <summary>
    /// Cross-SDK invariant log-record / MDC field name carrying the
    /// correlation ID.
    /// </summary>
    public const string MdcKey = "correlation_id";

    private static readonly AsyncLocal<string?> _current = new();

    /// <summary>The correlation ID currently in scope, or <see langword="null"/>.</summary>
    public static string? Current => _current.Value;

    /// <summary>
    /// Push <paramref name="correlationId"/> onto the current async flow
    /// for the lifetime of the returned <see cref="IDisposable"/>; the
    /// previous value (if any) is restored on dispose.
    /// </summary>
    public static IDisposable Scope(string correlationId)
    {
        ArgumentException.ThrowIfNullOrEmpty(correlationId);
        var previous = _current.Value;
        _current.Value = correlationId;
        return new Restorer(previous);
    }

    private sealed class Restorer : IDisposable
    {
        private readonly string? _previous;
        private bool _disposed;

        public Restorer(string? previous)
        {
            _previous = previous;
        }

        public void Dispose()
        {
            if (_disposed)
            {
                return;
            }

            _current.Value = _previous;
            _disposed = true;
        }
    }
}
