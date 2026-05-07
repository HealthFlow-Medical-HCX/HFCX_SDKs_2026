# HFCX SDKs

Official Software Development Kits for the Egyptian Health Claims eXchange (HFCX) platform.

This repository hosts the Java, Python, .NET, and JavaScript SDKs that
implement the HFCX wire protocol — JWE-protected FHIR R4 payloads on top of
the Egyptian Implementation Guide.

The first SDK (`sdk-java/`) lands in Sprint J1; subsequent sprints fill out
the Keycloak token client (J2), registry client (J3), eligibility/preauth/
claim helpers (J4–J5), FHIR validation wiring (J6), examples and
release-engineering (J7).

See `CONTRIBUTING.md` and `docs/CROSS_SDK_PARITY.md` for delivery discipline.
