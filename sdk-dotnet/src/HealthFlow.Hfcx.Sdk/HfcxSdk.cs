// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Reflection;

namespace HealthFlow.Hfcx.Sdk;

/// <summary>
/// Top-level constants for the HFCX SDK for .NET.
/// </summary>
/// <remarks>
/// Sister to <c>HfcxSdkVersion</c> in the Java SDK and
/// <c>hfcx_sdk.__version__</c> in the Python SDK. Cross-SDK invariant:
/// <see cref="Version"/> tracks the SDK release; the platform compatibility
/// version is recorded in <c>fhir-ig/PLATFORM_VERSION</c> and surfaced via
/// <see cref="BundledIgVersion"/>.
/// </remarks>
public static class HfcxSdk
{
    /// <summary>
    /// Sentinel returned by <see cref="BundledIgVersion"/> when no IG package
    /// has been synced yet.
    /// </summary>
    public const string Unbundled = "unbundled";

    /// <summary>
    /// Semver release identifier of this SDK build, e.g. <c>"1.0.0"</c>.
    /// </summary>
    /// <remarks>
    /// Read at runtime from the assembly's <see cref="AssemblyInformationalVersionAttribute"/>
    /// so the value cannot drift between the csproj and the source. The
    /// build sets this from <c>Directory.Build.props</c>'s
    /// <c>InformationalVersion</c> property.
    /// </remarks>
    public static string Version { get; } = ReadInformationalVersion();

    /// <summary>
    /// The platform version the bundled Egyptian FHIR IG package was synced
    /// from, or <see cref="Unbundled"/> if no package is bundled yet.
    /// </summary>
    /// <remarks>
    /// Sister to <c>HfcxSdkVersion.COMPATIBLE_PLATFORM_VERSION</c> on the
    /// Java SDK and <c>hfcx_sdk.bundled_ig_version()</c> on the Python SDK.
    /// Reads <c>fhir-ig/PLATFORM_VERSION</c> on first access; the value is
    /// captured into the assembly via a generated source file in a future
    /// sprint, but for D1 we read the file at runtime to keep the bootstrap
    /// minimal.
    /// </remarks>
    public static string BundledIgVersion { get; } = ReadBundledIgVersion();

    private static string ReadInformationalVersion()
    {
        var attr = typeof(HfcxSdk).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>();
        var value = attr?.InformationalVersion;
        if (string.IsNullOrWhiteSpace(value))
        {
            return "0.0.0";
        }

        // SourceLink appends a "+<commit>" suffix to InformationalVersion
        // on CI builds; strip it so the value matches the csproj literal.
        var plus = value.IndexOf('+', System.StringComparison.Ordinal);
        return plus >= 0 ? value[..plus] : value;
    }

    private static string ReadBundledIgVersion()
    {
        // For Sprint D1 we don't ship the IG package yet; the helper exists
        // so the public surface is stable across the swap.
        try
        {
            var asmDir = System.IO.Path.GetDirectoryName(typeof(HfcxSdk).Assembly.Location);
            if (asmDir is null)
            {
                return Unbundled;
            }

            // Walk up from the assembly dir to find sdk-dotnet/fhir-ig/PLATFORM_VERSION.
            var dir = new System.IO.DirectoryInfo(asmDir);
            while (dir is not null)
            {
                var candidate = System.IO.Path.Combine(dir.FullName, "fhir-ig", "PLATFORM_VERSION");
                if (System.IO.File.Exists(candidate))
                {
                    var text = System.IO.File.ReadAllText(candidate).Trim();
                    return string.IsNullOrEmpty(text) ? Unbundled : text;
                }

                dir = dir.Parent;
            }
        }
        catch (System.IO.IOException)
        {
            // Fail-secure: no exception escapes a static field initialiser.
        }
        catch (System.UnauthorizedAccessException)
        {
        }

        return Unbundled;
    }
}
