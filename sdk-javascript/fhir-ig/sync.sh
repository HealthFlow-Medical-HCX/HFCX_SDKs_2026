#!/usr/bin/env bash
# fhir-ig/sync.sh — sync the bundled Egyptian FHIR IG from a tagged
# platform release into sdk-javascript/fhir-ig/.
#
# Usage:
#   ./fhir-ig/sync.sh <platform-version-tag> <expected-sha256>
#
# Mirror of sdk-java/fhir-ig/sync.sh and sdk-python/fhir-ig/sync.sh.

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <platform-version-tag> <expected-sha256>" >&2
  exit 1
fi

tag="$1"
expected_sha="$2"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="HealthFlow-Medical-HCX/hfcx-platform"
url="https://github.com/${repo}/releases/download/${tag}/egyptian-ig.tgz"
tmp="$(mktemp -d)"
trap 'rm -rf "${tmp}"' EXIT

echo "Downloading ${url} ..." >&2
curl --fail --location --silent --show-error --output "${tmp}/egyptian-ig.tgz" "${url}"

actual_sha="$(shasum -a 256 "${tmp}/egyptian-ig.tgz" | awk '{print $1}')"
if [[ "${actual_sha}" != "${expected_sha}" ]]; then
  echo "SHA256 mismatch: expected ${expected_sha}, got ${actual_sha}" >&2
  exit 2
fi

mv "${tmp}/egyptian-ig.tgz" "${script_dir}/egyptian-ig.tgz"
echo -n "${tag}" > "${script_dir}/PLATFORM_VERSION"

echo "Synced ${tag} (sha256 ${actual_sha:0:12}…) into ${script_dir}/" >&2
