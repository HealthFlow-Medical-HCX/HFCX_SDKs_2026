// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Threading;
using System.Threading.Tasks;

namespace HealthFlow.Hfcx.Sdk.Registry;

/// <summary>
/// Abstraction over the registry lookup so callers and tests can substitute
/// in-memory resolvers, fixtures, or alternative registries.
/// </summary>
/// <remarks>
/// Sister to the Java SDK's <c>RecipientCertResolver</c>
/// <c>@FunctionalInterface</c> and the Python SDK's
/// <c>RecipientCertResolver</c> Protocol. Cross-SDK invariant: same input →
/// same <see cref="ParticipantCert"/> across SDKs.
/// </remarks>
public interface IRecipientCertResolver
{
    /// <summary>
    /// Look up the encryption cert for <paramref name="participantCode"/>.
    /// </summary>
    /// <exception cref="HealthFlow.Hfcx.Sdk.Exceptions.ParticipantNotFoundException">
    /// when the registry returns 404 for this participant code.
    /// </exception>
    /// <exception cref="HealthFlow.Hfcx.Sdk.Exceptions.RegistryUnavailableException">
    /// when the registry is unreachable after retry exhaustion.
    /// </exception>
    Task<ParticipantCert> GetRecipientCertAsync(
        string participantCode,
        CancellationToken cancellationToken = default);
}
