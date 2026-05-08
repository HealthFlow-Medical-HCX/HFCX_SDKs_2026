// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.IO;
using System.Security.Cryptography;
using HealthFlow.Hfcx.Sdk.Exceptions;

namespace HealthFlow.Hfcx.Sdk.Recipient;

/// <summary>
/// Loads the recipient's RSA private key from a PKCS#8 PEM file. The
/// key file is re-read on every <see cref="GetPrivateKey"/> call so
/// rotations take effect without restarting the host process. Sister to
/// Java's <c>FileLocalKeyProvider</c> and Python's
/// <c>FileLocalKeyProvider</c>.
/// </summary>
public sealed class FileLocalKeyProvider : ILocalKeyProvider
{
    private readonly string _path;

    /// <summary>Construct over the given filesystem path.</summary>
    public FileLocalKeyProvider(string path)
    {
        ArgumentException.ThrowIfNullOrEmpty(path);
        _path = path;
    }

    /// <inheritdoc />
    public RSA GetPrivateKey()
    {
        string pem;
        try
        {
            pem = File.ReadAllText(_path);
        }
        catch (Exception ex) when (ex is FileNotFoundException
            or DirectoryNotFoundException
            or IOException
            or UnauthorizedAccessException)
        {
            throw new KeyUnavailableException(
                $"Failed to read recipient private key from '{_path}': {ex.Message}");
        }

        var rsa = RSA.Create();
        try
        {
            rsa.ImportFromPem(pem);
            return rsa;
        }
        catch (Exception ex) when (ex is ArgumentException or CryptographicException)
        {
            rsa.Dispose();
            throw new KeyUnavailableException(
                $"Failed to parse recipient private key PEM at '{_path}': {ex.Message}");
        }
    }
}
