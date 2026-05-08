// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using HealthFlow.Hfcx.Sdk;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

public class HfcxSdkTests
{
    [Fact]
    public void Version_IsNotEmpty()
    {
        Assert.False(string.IsNullOrWhiteSpace(HfcxSdk.Version));
    }

    [Fact]
    public void Version_HasSemverShape()
    {
        // Allow optional pre-release suffix (e.g. "0.1.0-alpha.0").
        var match = System.Text.RegularExpressions.Regex.IsMatch(
            HfcxSdk.Version,
            @"^\d+\.\d+\.\d+(?:[-.][\w.-]+)?$");
        Assert.True(match, $"unexpected version shape: {HfcxSdk.Version}");
    }

    [Fact]
    public void Version_MatchesAssemblyInformationalVersion()
    {
        var attr = typeof(HfcxSdk).Assembly
            .GetCustomAttributes(typeof(System.Reflection.AssemblyInformationalVersionAttribute), false);
        Assert.NotEmpty(attr);
        var infoVer = ((System.Reflection.AssemblyInformationalVersionAttribute)attr[0]).InformationalVersion;
        // Strip the SourceLink "+commit" suffix the same way HfcxSdk does.
        var plus = infoVer.IndexOf('+', System.StringComparison.Ordinal);
        var expected = plus >= 0 ? infoVer[..plus] : infoVer;
        Assert.Equal(expected, HfcxSdk.Version);
    }

    [Fact]
    public void BundledIgVersion_DefaultsToUnbundledSentinel()
    {
        // Sprint D1 ships without a synced fhir-ig/egyptian-ig.tgz; the
        // PLATFORM_VERSION file is empty. Once a real platform release is
        // synced this test gets updated to assert the actual tag.
        Assert.Equal(HfcxSdk.Unbundled, HfcxSdk.BundledIgVersion);
    }

    [Fact]
    public void Unbundled_SentinelIsPinned()
    {
        Assert.Equal("unbundled", HfcxSdk.Unbundled);
    }
}
