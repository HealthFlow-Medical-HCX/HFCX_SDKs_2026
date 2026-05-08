# Releasing the HFCX SDK for .NET

This document is the canonical procedure for cutting a GA release of
`HealthFlow.Hfcx.Sdk` to nuget.org. Sprint D7 ships everything except
the final tag/publish step — the steps below are what a maintainer
with the nuget.org publish entitlement runs.

Sister to [`sdk-java/RELEASING.md`](../sdk-java/RELEASING.md) and
[`sdk-python/RELEASING.md`](../sdk-python/RELEASING.md). Same shape,
different toolchain.

## Prerequisites (one-time)

1. Register the `HealthFlow.Hfcx.Sdk` package on nuget.org under an
   organisation account that the `HealthFlow-Medical-HCX` GitHub org
   can publish to.
2. Generate an API key scoped to the package and add it to this
   repository as the `NUGET_API_KEY` GitHub Secret.
3. Set the `NUGET_TRUSTED_PUBLISHER_CONFIGURED` GitHub repo variable
   to `true` so the workflow does the real push instead of the
   build-only smoke.

The publish workflow (`.github/workflows/dotnet-publish.yml`) is
already wired against these — no further configuration required.

## Per-release procedure

### 1. Verify the working tree is releasable

```bash
git checkout main
git pull --ff-only
cd sdk-dotnet
dotnet restore
dotnet build -c Release --no-restore
dotnet test  -c Release --no-build
```

All .NET tests must be green before continuing. The
`PlatformMockPayer` integration tests are not yet wired in (Sprint
D8+); the hermetic `HfcxClientTests` and `RecipientHandlerTests`
cover the same matrix in-process.

Run the ASP.NET Core example integration tests once as well:

```bash
cd docs/examples/recipient-aspnet
dotnet test tests/RecipientAspNetExample.Tests
```

### 2. Bump the version

```bash
sed -i 's|<Version>0\.[0-9].*</Version>|<Version>1.0.0</Version>|'                           sdk-dotnet/Directory.Build.props
sed -i 's|<FileVersion>0\.[0-9].*</FileVersion>|<FileVersion>1.0.0.0</FileVersion>|'         sdk-dotnet/Directory.Build.props
sed -i 's|<AssemblyVersion>0\.[0-9].*</AssemblyVersion>|<AssemblyVersion>1.0.0.0</AssemblyVersion>| sdk-dotnet/Directory.Build.props
sed -i 's|<InformationalVersion>0\.[0-9].*</InformationalVersion>|<InformationalVersion>1.0.0</InformationalVersion>| sdk-dotnet/Directory.Build.props
```

Verify the bump landed and the package metadata still builds:

```bash
cd sdk-dotnet
dotnet pack src/HealthFlow.Hfcx.Sdk -c Release --output ./artifacts
ls ./artifacts                     # HealthFlow.Hfcx.Sdk.1.0.0.nupkg + .snupkg
```

`HfcxSdk.Version` reads `[AssemblyInformationalVersion]` at runtime,
so it picks up the new value automatically — no source change is
needed.

Update the FHIR IG sync if a new platform release exists:

```bash
./fhir-ig/sync.sh <platform-version-tag> <expected-sha256>
```

`HfcxSdk.BundledIgVersion` then returns the new tag instead of
`"unbundled"` on first access (the helper walks up from the assembly
location to find `fhir-ig/PLATFORM_VERSION`).

### 3. Update the changelog and release notes

- Move the `## [Unreleased]` block in `sdk-dotnet/CHANGELOG.md` to
  `## [1.0.0] — YYYY-MM-DD` and start a fresh `## [Unreleased]`.
- Promote `sdk-dotnet/docs/releases/v1.0.0.md` from a placeholder to
  the actual release notes (date, GA highlights, link to the parity
  audit).
- Mirror the change in the top-level `CHANGELOG.md`.
- Run the parity audit and confirm every .NET row is `✅`:
  ```bash
  python scripts/audit_parity.py --sdk dotnet
  ```

### 4. Commit the release

```bash
git add -A
git commit -m "release: HealthFlow.Hfcx.Sdk v1.0.0"
git tag -s sdk-dotnet/v1.0.0 -m "HFCX SDK for .NET 1.0.0"
git push origin main
git push origin sdk-dotnet/v1.0.0
```

The tag push fires `.github/workflows/dotnet-publish.yml` which:

1. Restores + builds in `Release` configuration.
2. Runs the full xUnit test suite.
3. Calls `dotnet pack` to produce the `.nupkg` (+ `.snupkg` symbol
   package) under `./artifacts`.
4. Pushes both to nuget.org via `dotnet nuget push --skip-duplicate`
   when `NUGET_TRUSTED_PUBLISHER_CONFIGURED=true`. Otherwise the
   workflow stops at a build-only smoke and uploads the artefacts as
   a workflow artifact.

### 5. Smoke test the published artefact

While nuget.org indexes the new version (typically a minute or two),
prepare a clean smoke env:

```bash
mkdir /tmp/hfcx-stage-smoke && cd /tmp/hfcx-stage-smoke
cat > probe.csproj <<'EOF'
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net8.0</TargetFramework>
    <Nullable>enable</Nullable>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="HealthFlow.Hfcx.Sdk" Version="1.0.0" />
  </ItemGroup>
</Project>
EOF
cat > Program.cs <<'EOF'
using HealthFlow.Hfcx.Sdk;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Validators;

System.Console.WriteLine($"version={HfcxSdk.Version}");
System.Console.WriteLine($"bundled-ig={HfcxSdk.BundledIgVersion}");
System.Console.WriteLine($"err={ErrorCode.NationalIdInvalid.Code}");
System.Console.WriteLine($"gov={EgyptianGovernorate.Cairo.EnglishName}");
EOF
dotnet run
```

Expected output:

```
version=1.0.0
bundled-ig=<tag or "unbundled">
err=ERR-B-006
gov=Cairo
```

### 6. Post-release housekeeping

- Bump back to `1.0.1-alpha.0` in `sdk-dotnet/Directory.Build.props`
  on `main` and push.
- Open a PR against `HealthFlow-Medical-HCX/hfcx-platform` updating
  `docs/strategy/sdk-delivery-plan.md` to mark .NET SDK 1.0.0 done
  with the release date.
- Announce the release in the platform's release-notes channel.

## Rolling back a bad release

NuGet.org releases are **immutable** — you cannot republish a version.
If a critical bug ships:

1. Unlist the bad version (keeps installs but blocks new resolution):
   ```bash
   dotnet nuget delete HealthFlow.Hfcx.Sdk 1.0.0 --source https://api.nuget.org/v3/index.json
   ```
   Unlisting is reversible from the nuget.org web UI if the bug
   turns out to be a false alarm.
2. Cut `1.0.1` immediately with the fix.
3. Open a security advisory on GitHub if the bug has security
   implications.

Avoid this by running steps 1–5 carefully every time.
