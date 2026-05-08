"""Live integration test against the platform's mock-payer container.

Sister to the Java SDK's
:class:`PlatformMockPayerIntegrationTest`. Skipped by default;
activated by tagging ``-m platform_integration`` once the platform's
``tests/integration/`` Docker stack is up.

Reproduction (when the stack is up):

.. code-block:: bash

    pytest -m platform_integration tests/integration/test_platform_mock_payer.py

Required environment variables, mirroring the Java equivalent:

* ``HFCX_GATEWAY_URL``
* ``HFCX_KEYCLOAK_TOKEN_URL``
* ``HFCX_KEYCLOAK_CLIENT_ID``
* ``HFCX_KEYCLOAK_CLIENT_SECRET``
* ``HFCX_REGISTRY_BASE_URL``
* ``HFCX_SENDER_PARTICIPANT_CODE``
* ``HFCX_RECIPIENT_PRIVATE_KEY_PATH``

Each cycle (eligibility, preauth, claim, communication, payment
notice) posts a fixture FHIR Bundle through the SDK and asserts
HTTP 202 + correlation-ID echo. Inverse direction (mock-provider →
SDK as recipient) is covered once Sprint P5 lands the
:class:`hfcx_sdk.recipient.RecipientHandler`.
"""

from __future__ import annotations

import pytest

pytestmark = [
    pytest.mark.platform_integration,
    pytest.mark.skip(reason="requires hfcx-platform tests/integration stack; see module docstring"),
]


def test_cycle31_1_eligibility_accepted_by_mock_payer() -> None:
    raise NotImplementedError("Implement when the platform-integration CI job lands.")


def test_cycle31_2_preauth_accepted_by_mock_payer() -> None:
    raise NotImplementedError("Implement when the platform-integration CI job lands.")


def test_cycle31_3_claim_accepted_by_mock_payer() -> None:
    raise NotImplementedError("Implement when the platform-integration CI job lands.")


def test_cycle31_4_payment_notice_accepted_by_mock_payer() -> None:
    raise NotImplementedError("Implement when the platform-integration CI job lands.")


def test_cycle31_5_communication_accepted_by_mock_payer() -> None:
    raise NotImplementedError("Implement when the platform-integration CI job lands.")
