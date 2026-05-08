// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Generates self-signed RSA encryption certs for the registry tests so
/// the <c>encryption_cert</c> field in mock registry responses is real
/// PEM the SDK's <see cref="System.Security.Cryptography.X509Certificates.X509Certificate2"/>
/// loader actually parses.
/// </summary>
internal static class RegistryTestFixtures
{
    public static (string CertPem, RSA PrivateKey, DateTimeOffset NotAfter) NewSelfSignedCert(
        TimeSpan? validFor = null)
    {
        var rsa = RSA.Create(2048);
        try
        {
            var req = new CertificateRequest(
                "CN=hfcx-registry-test",
                rsa,
                HashAlgorithmName.SHA256,
                RSASignaturePadding.Pkcs1);

            var notBefore = DateTimeOffset.UtcNow.AddMinutes(-5);
            var notAfter = notBefore + (validFor ?? TimeSpan.FromDays(30));

            using var cert = req.CreateSelfSigned(notBefore, notAfter);
            var pem = ExportPem(cert);
            return (pem, rsa, notAfter);
        }
        catch
        {
            rsa.Dispose();
            throw;
        }
    }

    public static string BuildRegistryResponse(string participantCode, string certPem)
    {
        var escaped = certPem.Replace("\r", "", StringComparison.Ordinal).Replace("\n", "\\n", StringComparison.Ordinal);
        return $$"""
            {
              "entity": [
                {
                  "participant_code": "{{participantCode}}",
                  "encryption_cert": "{{escaped}}"
                }
              ]
            }
            """;
    }

    private static string ExportPem(X509Certificate2 cert)
    {
        var b64 = Convert.ToBase64String(cert.RawData);
        var sb = new StringBuilder();
        sb.AppendLine("-----BEGIN CERTIFICATE-----");
        for (var i = 0; i < b64.Length; i += 64)
        {
            sb.AppendLine(b64.Substring(i, Math.Min(64, b64.Length - i)));
        }

        sb.AppendLine("-----END CERTIFICATE-----");
        return sb.ToString();
    }
}
