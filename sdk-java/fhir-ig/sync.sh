#!/usr/bin/env bash
# Syncs the Egyptian FHIR IG package from the hfcx-platform repo and
# updates both the human-readable PLATFORM_VERSION marker and the
# <platform.version> Maven property in sdk-java/pom.xml.
#
# Usage:
#   fhir-ig/sync.sh <platform-version-tag> [<expected-sha256>]
#
# If <expected-sha256> is provided, the downloaded tarball is verified
# against it before any other file is written. If it is omitted, the
# script tries to fetch a sibling `egyptian-ig.tgz.sha256` file from the
# same release. If neither is available, the script aborts.
set -euo pipefail

PLATFORM_VERSION="${1:?usage: sync.sh <platform-version-tag> [<expected-sha256>]}"
EXPECTED_SHA="${2:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_JAVA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
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
    # The release artifact may be either a bare hex digest or
    # "<digest>  <filename>" (sha256sum format). Handle both.
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

# Update the <platform.version> property in the parent POM so
# HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION reflects the new IG.
# Falls back to in-place sed if the versions plugin is unavailable.
if command -v mvn >/dev/null 2>&1; then
    (cd "${SDK_JAVA_DIR}" && mvn -B -ntp \
        org.codehaus.mojo:versions-maven-plugin:2.16.2:set-property \
        -Dproperty=platform.version \
        -DnewVersion="${PLATFORM_VERSION}" \
        -DgenerateBackupPoms=false)
else
    echo "mvn not on PATH; falling back to sed for parent pom update."
    sed -i.bak \
        -E "s|(<platform\\.version>)[^<]*(</platform\\.version>)|\\1${PLATFORM_VERSION}\\2|" \
        "${SDK_JAVA_DIR}/pom.xml"
    rm -f "${SDK_JAVA_DIR}/pom.xml.bak"
fi

echo "Synced IG to ${PLATFORM_VERSION}"
echo "Next:"
echo "  git add ${SCRIPT_DIR}/egyptian-ig.tgz ${SCRIPT_DIR}/PLATFORM_VERSION ${SDK_JAVA_DIR}/pom.xml"
echo "  git commit -m \"chore(java): sync FHIR IG to platform ${PLATFORM_VERSION}\""
