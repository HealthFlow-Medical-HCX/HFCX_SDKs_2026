package eg.gov.healthflow.hfcx.sdk.client.registry;

/**
 * Abstraction over the registry lookup so callers (e.g.
 * {@code OutboundEncryptor}) and tests can substitute in-memory
 * resolvers, fixtures, or alternative registries.
 *
 * <p>The default implementation is {@link RegistryClient}, which talks to
 * the platform's Sunbird-RC participant registry over HTTP with a
 * Caffeine cache. Production code typically uses {@code RegistryClient}
 * directly; tests use a lambda or fixture map.
 */
@FunctionalInterface
public interface RecipientCertResolver {

    /**
     * @return the cached or freshly fetched encryption cert for {@code participantCode}.
     */
    ParticipantCert getRecipientCert(String participantCode);
}
