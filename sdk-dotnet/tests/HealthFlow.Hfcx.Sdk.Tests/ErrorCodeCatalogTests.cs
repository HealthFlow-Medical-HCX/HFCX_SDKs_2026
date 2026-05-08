// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Collections.Generic;
using System.Linq;
using HealthFlow.Hfcx.Sdk.Exceptions;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Cross-SDK invariant: this catalog must match the Java SDK's
/// <c>ErrorCodeCatalogTest</c> and the Python SDK's
/// <c>test_error_code_catalog.py</c>. Wire-code uniqueness, canonical
/// format, tier-prefix consistency, factory dispatch, per-tier counts.
/// </summary>
public class ErrorCodeCatalogTests
{
    [Fact]
    public void Catalog_Has27Entries()
    {
        Assert.Equal(27, ErrorCode.All.Count);
    }

    [Fact]
    public void Catalog_HasNineProtocolEntries()
    {
        Assert.Equal(9, ErrorCode.All.Count(e => e.Tier == Tier.Protocol));
    }

    [Fact]
    public void Catalog_HasTwelveBusinessEntries()
    {
        Assert.Equal(12, ErrorCode.All.Count(e => e.Tier == Tier.Business));
    }

    [Fact]
    public void Catalog_HasSixTechnicalEntries()
    {
        Assert.Equal(6, ErrorCode.All.Count(e => e.Tier == Tier.Technical));
    }

    [Fact]
    public void Catalog_WireCodesAreUnique()
    {
        var codes = ErrorCode.All.Select(e => e.Code).ToList();
        Assert.Equal(codes.Count, codes.Distinct().Count());
    }

    [Theory]
    [MemberData(nameof(EveryEntry))]
    public void Catalog_WireCodesMatchCanonicalFormat(ErrorCode entry)
    {
        Assert.Matches(@"^ERR-[PBT]-\d{3}$", entry.Code);
    }

    [Theory]
    [MemberData(nameof(EveryEntry))]
    public void Catalog_TierPrefixIsConsistent(ErrorCode entry)
    {
        var prefix = entry.Code[4];
        switch (entry.Tier)
        {
            case Tier.Protocol:
                Assert.Equal('P', prefix);
                break;
            case Tier.Business:
                Assert.Equal('B', prefix);
                break;
            case Tier.Technical:
                Assert.Equal('T', prefix);
                break;
            default:
                Assert.Fail($"unexpected tier: {entry.Tier}");
                break;
        }
    }

    [Theory]
    [MemberData(nameof(EveryEntry))]
    public void Catalog_DescriptionsAreNonEmpty(ErrorCode entry)
    {
        Assert.False(string.IsNullOrWhiteSpace(entry.Description));
    }

    [Fact]
    public void FromWire_KnownCodeReturnsEntry()
    {
        Assert.Same(ErrorCode.MissingHeader, ErrorCode.FromWire("ERR-P-001"));
        Assert.Same(ErrorCode.NationalIdInvalid, ErrorCode.FromWire("ERR-B-006"));
        Assert.Same(ErrorCode.Authentication, ErrorCode.FromWire("ERR-T-002"));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("ERR-X-999")]
    [InlineData("not-a-code")]
    public void FromWire_UnknownReturnsNull(string? wireCode)
    {
        Assert.Null(ErrorCode.FromWire(wireCode));
    }

    [Theory]
    [MemberData(nameof(EveryEntry))]
    public void Of_ReturnsMostSpecificTypedSubclass(ErrorCode entry)
    {
        var ex = HfcxException.Of(entry, "test");
        Assert.Equal(entry.Code, ex.Code);
        // Must not return the bare tier base class.
        Assert.NotEqual(typeof(ProtocolException), ex.GetType());
        Assert.NotEqual(typeof(BusinessException), ex.GetType());
        Assert.NotEqual(typeof(TechnicalException), ex.GetType());
        // And the message must round-trip.
        Assert.Equal("test", ex.Message);
    }

    [Theory]
    [MemberData(nameof(EveryEntry))]
    public void Of_TierIsConsistentWithSubclass(ErrorCode entry)
    {
        var ex = HfcxException.Of(entry, "test");
        switch (entry.Tier)
        {
            case Tier.Protocol:
                Assert.IsAssignableFrom<ProtocolException>(ex);
                break;
            case Tier.Business:
                Assert.IsAssignableFrom<BusinessException>(ex);
                break;
            case Tier.Technical:
                Assert.IsAssignableFrom<TechnicalException>(ex);
                break;
            default:
                Assert.Fail($"unexpected tier: {entry.Tier}");
                break;
        }
    }

    [Fact]
    public void FromWireCode_KnownCodeReturnsTypedSubclass()
    {
        var ex = HfcxException.FromWireCode("ERR-B-006", "bad nid");
        Assert.IsType<NationalIdInvalidException>(ex);
        Assert.Equal("ERR-B-006", ex.Code);
    }

    [Fact]
    public void FromWireCode_UnknownCodeFallsBackToTierBase()
    {
        var p = HfcxException.FromWireCode("ERR-P-999", "x");
        Assert.IsType<ProtocolException>(p);
        Assert.Equal("ERR-P-999", p.Code);

        var b = HfcxException.FromWireCode("ERR-B-999", "x");
        Assert.IsType<BusinessException>(b);

        var t = HfcxException.FromWireCode("ERR-T-999", "x");
        Assert.IsType<TechnicalException>(t);
    }

    [Fact]
    public void FromWireCode_GarbageCodeFallsBackToUnknownBusiness()
    {
        var ex = HfcxException.FromWireCode("nonsense", "x");
        Assert.IsType<UnknownBusinessException>(ex);
        Assert.Contains("nonsense", ex.Message);
    }

    [Fact]
    public void FromWireCode_PreservesPlatformReportedCodeOnFallback()
    {
        var ex = HfcxException.FromWireCode("ERR-P-042", "future code");
        Assert.Equal("ERR-P-042", ex.Code);
    }

    [Fact]
    public void TypedSubclasses_PinTheRightCode()
    {
        Assert.Equal(ErrorCode.MissingHeader.Code, MissingHeaderException.CodeValue);
        Assert.Equal(ErrorCode.NationalIdInvalid.Code, NationalIdInvalidException.CodeValue);
        Assert.Equal(ErrorCode.Authentication.Code, AuthenticationException.CodeValue);
        Assert.Equal(ErrorCode.Gateway5xx.Code, Gateway5xxException.CodeValue);
    }

    public static IEnumerable<object[]> EveryEntry =>
        ErrorCode.All.Select(e => new object[] { e });
}
