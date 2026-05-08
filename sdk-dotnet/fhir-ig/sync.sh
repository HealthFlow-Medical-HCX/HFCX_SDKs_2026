#!/usr/bin/env bash
# Syncs the Egyptian FHIR IG package from the hfcx-platform repo.
# Identical shape to sdk-java/fhir-ig/sync.sh — same platform release,
# same SHA256 verification, same PLATFORM_VERSION format.
set -euo pipefail

PLATFORM_VERSION="${1:?usage: sync.sh <platform-version-tag> [<expected-sha256>]}"
EXPECTED_SHA="${2:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARBALL_URL="https://github.com/HealthFlow-Medical-HCX/hfcx-platform/releases/download/${PLATFORM_VERSION}/egyptian-ig.tgz"
SHA_URL="${TARBALL_URL}.sha256"

TMPDIR="$(mktemp -d)"
trap 'rm -rf "${TMPDIR}"' EXIT

echo "Fetching IG package from ${TARBALL_URL}"
curl -fLo "${TMPDIR}/egyptian-ig.tgz" "${TARBALL_URL}"

if [[ -z "${EXPECTED_SHA}" ]]; then
    echo "No SHA256 supplied on the command line — fetching ${SHA_URL}"
    if ! curl -fLo "${TMPDIR}/egyptian-ig.tgz.sha256" "${SHA_URL}"; then
        echo "ERROR: no checksum supplied and ${SHA_URL} is unreachable." >&2
        echo "       Re-run with: fhir-ig/sync.sh ${PLATFORM_VERSION} <sha256>" >&2
        exit 1
    fi
    EXPECTED_SHA="$(awk '{print $1}' "${TMPDIR}/egyptian-ig.tgz.sha256")"
fi

if [[ ! "${EXPECTED_SHA}" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "ERROR: expected SHA256 is not 64 hex chars: ${EXPECTED_SHA}" >&2
    exit 1
fi

ACTUAL_SHA="$(sha256sum "${TMPDIR}/egyptian-ig.tgz" | awk '{print $1}')"
if [[ "${ACTUAL_SHA,,}" != "${EXPECTED_SHA,,}" ]]; then
    echo "ERROR: SHA256 mismatch for egyptian-ig.tgz" >&2
    echo "  expected: ${EXPECTED_SHA}" >&2
    echo "  actual:   ${ACTUAL_SHA}" >&2
    exit 1
fi
echo "SHA256 verified: ${ACTUAL_SHA}"

mv "${TMPDIR}/egyptian-ig.tgz" "${SCRIPT_DIR}/egyptian-ig.tgz"
echo "${PLATFORM_VERSION}" > "${SCRIPT_DIR}/PLATFORM_VERSION"

echo "Synced IG to ${PLATFORM_VERSION}"
echo "Next: git add ${SCRIPT_DIR}/egyptian-ig.tgz ${SCRIPT_DIR}/PLATFORM_VERSION"
