// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.IO;
using System.Security.Cryptography;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Loaders for the cross-SDK round-trip fixtures shared with the Java and
/// Python SDKs. Every SDK reads <c>private-key.pem</c> /
/// <c>public-key.pem</c> / <c>plaintext.json</c> from the same byte-for-byte
/// fixtures so any drift in JWE wire format shows up immediately as a test
/// failure.
/// </summary>
internal static class CrossSdkFixtures
{
    public static string FixtureDir
    {
        get
        {
            var dir = Path.Combine(AppContext.BaseDirectory, "fixtures", "cross-sdk");
            if (!Directory.Exists(dir))
            {
                throw new DirectoryNotFoundException(
                    $"cross-sdk fixtures missing at {dir}; check csproj Copy rule");
            }

            return dir;
        }
    }

    public static RSA LoadPrivateKey()
    {
        var rsa = RSA.Create();
        rsa.ImportFromPem(File.ReadAllText(Path.Combine(FixtureDir, "private-key.pem")));
        return rsa;
    }

    public static RSA LoadPublicKey()
    {
        var rsa = RSA.Create();
        rsa.ImportFromPem(File.ReadAllText(Path.Combine(FixtureDir, "public-key.pem")));
        return rsa;
    }

    public static string LoadPlaintext()
        => File.ReadAllText(Path.Combine(FixtureDir, "plaintext.json"));

    public static string LoadJavaProducedJwe()
        => File.ReadAllText(Path.Combine(FixtureDir, "java-produced.jwe")).Trim();

    public static string LoadPythonProducedJwe()
        => File.ReadAllText(Path.Combine(FixtureDir, "python-produced.jwe")).Trim();

    public static string LoadJavaScriptProducedJwe()
        => File.ReadAllText(Path.Combine(FixtureDir, "javascript-produced.jwe")).Trim();
}
