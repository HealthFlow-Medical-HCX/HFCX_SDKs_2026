"""Official Python SDK for the HealthFlow HFCX platform.

Egypt's open protocol for decentralised health-claims data exchange.

Sprint progress:

* P1 ✅ — package skeleton + error catalog
* P2 ✅ — JWE encrypt / decrypt with cross-SDK round trip
* P3 ✅ — Keycloak token client + registry (sync + async)
* P4 ✅ — :class:`HfcxClient` outbound flow (sync + async)
* P5 ✅ — :class:`hfcx_sdk.recipient.RecipientHandler` + four-layer pipeline
* P6 ✅ — Egyptian validators + FHIR Bundle validation (hand-rolled, lockstep with Java)
* P7 ⏳ — 1.0.0 release
"""

from __future__ import annotations

from hfcx_sdk._logging import correlation_id_scope
from hfcx_sdk.client import (
    AsyncHfcxClient,
    CheckEligibilityRequest,
    HfcxClient,
    HfcxResponse,
    NotifyPaymentRequest,
    Operation,
    SendCommunicationRequest,
    Status,
    SubmitClaimRequest,
    SubmitPreauthRequest,
)
from hfcx_sdk.encryptor import (
    AsyncOutboundEncryptor,
    AsyncRecipientCertResolver,
    OutboundEncryptor,
)
from hfcx_sdk.exceptions import (
    AuthenticationError,
    BusinessError,
    ErrorCode,
    HfcxError,
    ProtocolError,
    TechnicalError,
)
from hfcx_sdk.fhir import bundled_ig_version
from hfcx_sdk.keycloak import AsyncKeycloakTokenClient, KeycloakTokenClient
from hfcx_sdk.recipient import (
    BearerTokenValidator,
    EgyptianBundleValidator,
    FhirValidator,
    FileLocalKeyProvider,
    HeaderValidator,
    InboundDecryptor,
    Layer,
    LocalKeyProvider,
    RecipientHandler,
    RecipientResult,
    VaultLocalKeyProvider,
)
from hfcx_sdk.registry import (
    AsyncRegistryClient,
    ParticipantCert,
    RecipientCertResolver,
    RegistryClient,
)

__version__ = "0.1.0a0"

__all__ = [
    "AsyncHfcxClient",
    "AsyncKeycloakTokenClient",
    "AsyncOutboundEncryptor",
    "AsyncRecipientCertResolver",
    "AsyncRegistryClient",
    "AuthenticationError",
    "BearerTokenValidator",
    "BusinessError",
    "CheckEligibilityRequest",
    "EgyptianBundleValidator",
    "ErrorCode",
    "FhirValidator",
    "FileLocalKeyProvider",
    "HeaderValidator",
    "HfcxClient",
    "HfcxError",
    "HfcxResponse",
    "InboundDecryptor",
    "KeycloakTokenClient",
    "Layer",
    "LocalKeyProvider",
    "NotifyPaymentRequest",
    "Operation",
    "OutboundEncryptor",
    "ParticipantCert",
    "ProtocolError",
    "RecipientCertResolver",
    "RecipientHandler",
    "RecipientResult",
    "RegistryClient",
    "SendCommunicationRequest",
    "Status",
    "SubmitClaimRequest",
    "SubmitPreauthRequest",
    "TechnicalError",
    "VaultLocalKeyProvider",
    "__version__",
    "bundled_ig_version",
    "correlation_id_scope",
]
