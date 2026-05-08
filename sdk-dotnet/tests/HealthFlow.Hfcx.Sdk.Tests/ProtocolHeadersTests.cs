// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Linq;
using HealthFlow.Hfcx.Sdk.Protocol;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant — the five protocol header names are pinned in the
/// mixed hyphen+underscore form per Gap 7. Sister to the Java SDK's
/// <c>ProtocolHeadersTest</c> and the Python SDK's <c>test_protocol.py</c>.
/// </summary>
public class ProtocolHeadersTests
{
    [Fact]
    public void HeaderNames_ArePinnedAsCrossSdkInvariant()
    {
        Assert.Equal("x-hcx-sender_code", ProtocolHeaders.SenderCode);
        Assert.Equal("x-hcx-recipient_code", ProtocolHeaders.RecipientCode);
        Assert.Equal("x-hcx-correlation_id", ProtocolHeaders.CorrelationId);
        Assert.Equal("x-hcx-timestamp", ProtocolHeaders.Timestamp);
        Assert.Equal("x-hcx-api-call-id", ProtocolHeaders.ApiCallId);
    }

    [Fact]
    public void Build_ProducesAllFiveHeadersInDeterministicOrder()
    {
        var ts = new DateTimeOffset(2026, 5, 8, 12, 34, 56, TimeSpan.Zero);
        var headers = ProtocolHeaders.Build(
            senderCode: "myhospital@hcx-egypt",
            recipientCode: "payerco@hcx-egypt",
            correlationId: "11111111-1111-1111-1111-111111111111",
            timestamp: ts,
            apiCallId: "22222222-2222-2222-2222-222222222222");

        var keys = headers.Keys.ToArray();
        Assert.Equal(
            new[]
            {
                "x-hcx-sender_code",
                "x-hcx-recipient_code",
                "x-hcx-correlation_id",
                "x-hcx-timestamp",
                "x-hcx-api-call-id",
            },
            keys);
    }

    [Fact]
    public void Build_FormatsTimestampAsIso8601Utc()
    {
        var ts = new DateTimeOffset(2026, 5, 8, 12, 34, 56, 789, TimeSpan.Zero);
        var headers = ProtocolHeaders.Build("a", "b", "c", ts, "d");

        Assert.Equal("2026-05-08T12:34:56.789Z", headers[ProtocolHeaders.Timestamp]);
    }

    [Fact]
    public void Build_ConvertsLocalTimestampToUtc()
    {
        var ts = new DateTimeOffset(2026, 5, 8, 14, 0, 0, TimeSpan.FromHours(2));
        var headers = ProtocolHeaders.Build("a", "b", "c", ts, "d");
        Assert.StartsWith("2026-05-08T12:00:00", headers[ProtocolHeaders.Timestamp], StringComparison.Ordinal);
        Assert.EndsWith("Z", headers[ProtocolHeaders.Timestamp], StringComparison.Ordinal);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    public void Build_RejectsEmptySenderCode(string? value)
    {
        Assert.ThrowsAny<ArgumentException>(() =>
            ProtocolHeaders.Build(value!, "b", "c", DateTimeOffset.UtcNow, "d"));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    public void Build_RejectsEmptyRecipientCode(string? value)
    {
        Assert.ThrowsAny<ArgumentException>(() =>
            ProtocolHeaders.Build("a", value!, "c", DateTimeOffset.UtcNow, "d"));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    public void Build_RejectsEmptyCorrelationId(string? value)
    {
        Assert.ThrowsAny<ArgumentException>(() =>
            ProtocolHeaders.Build("a", "b", value!, DateTimeOffset.UtcNow, "d"));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    public void Build_RejectsEmptyApiCallId(string? value)
    {
        Assert.ThrowsAny<ArgumentException>(() =>
            ProtocolHeaders.Build("a", "b", "c", DateTimeOffset.UtcNow, value!));
    }
}
