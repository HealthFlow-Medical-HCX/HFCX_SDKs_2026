// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.IO;
using System.Security.Cryptography;
using HealthFlow.Hfcx.Sdk.Crypto;

// Regenerates dotnet-produced.jwe from the shared cross-SDK fixture key
// pair and plaintext. Sister to sdk-python/tests/fixtures/cross-sdk/regenerate.py
// and the Java SDK's RegenerateCrossSdkJwe class. Run from the repo root:
//
//     dotnet run --project sdk-dotnet/tools/RegenerateCrossSdkJwe
//
// Optional first argument: path to the cross-sdk fixture directory.
// Defaults to sdk-python/tests/fixtures/cross-sdk/ since that's the
// canonical location.

var defaultFixtureDir = Path.Combine("sdk-python", "tests", "fixtures", "cross-sdk");
var fixtureDir = args.Length > 0 ? args[0] : defaultFixtureDir;

if (!Directory.Exists(fixtureDir))
{
    Console.Error.WriteLine($"fixture directory not found: {fixtureDir}");
    return 1;
}

var publicKeyPath = Path.Combine(fixtureDir, "public-key.pem");
var plaintextPath = Path.Combine(fixtureDir, "plaintext.json");
var outputPath = Path.Combine(fixtureDir, "dotnet-produced.jwe");

using var rsa = RSA.Create();
rsa.ImportFromPem(File.ReadAllText(publicKeyPath));
var plaintext = File.ReadAllText(plaintextPath);

var jwe = JweEncryption.EncryptUtf8(plaintext, rsa);
File.WriteAllText(outputPath, jwe);

Console.WriteLine($"wrote {outputPath} ({jwe.Length} chars)");
return 0;
