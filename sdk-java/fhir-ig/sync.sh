#!/usr/bin/env bash
# Syncs the Egyptian FHIR IG package from the hfcx-platform repo.
# Run when the platform releases a new IG version; commit the result.
set -euo pipefail

PLATFORM_VERSION="${1:?usage: sync.sh <platform-version-tag>}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
URL="https://github.com/HealthFlow-Medical-HCX/hfcx-platform/releases/download/${PLATFORM_VERSION}/egyptian-ig.tgz"

echo "Fetching IG package from ${URL}"
curl -fLo "${SCRIPT_DIR}/egyptian-ig.tgz" "${URL}"
echo "${PLATFORM_VERSION}" > "${SCRIPT_DIR}/PLATFORM_VERSION"

echo "Synced IG from ${PLATFORM_VERSION}"
echo "Next: git add ${SCRIPT_DIR}/egyptian-ig.tgz ${SCRIPT_DIR}/PLATFORM_VERSION"
