// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Threading.Tasks;
using HealthFlow.Hfcx.Sdk.Logging;
using Xunit;

namespace HealthFlow.Hfcx.Sdk.Tests;

public class CorrelationIdTests
{
    [Fact]
    public void MdcKey_IsCrossSdkInvariant()
    {
        // Same key Java's MDC and Python's record-attribute name use.
        Assert.Equal("correlation_id", CorrelationId.MdcKey);
    }

    [Fact]
    public void Scope_SetsAndRestoresValue()
    {
        Assert.Null(CorrelationId.Current);

        using (CorrelationId.Scope("abc"))
        {
            Assert.Equal("abc", CorrelationId.Current);
        }

        Assert.Null(CorrelationId.Current);
    }

    [Fact]
    public void Scope_NestsCorrectly()
    {
        using (CorrelationId.Scope("outer"))
        {
            Assert.Equal("outer", CorrelationId.Current);
            using (CorrelationId.Scope("inner"))
            {
                Assert.Equal("inner", CorrelationId.Current);
            }

            Assert.Equal("outer", CorrelationId.Current);
        }

        Assert.Null(CorrelationId.Current);
    }

    [Fact]
    public async Task Scope_FlowsAcrossAwait()
    {
        using (CorrelationId.Scope("flowed"))
        {
            await Task.Yield();
            Assert.Equal("flowed", CorrelationId.Current);
            await Task.Delay(1);
            Assert.Equal("flowed", CorrelationId.Current);
        }
    }

    [Fact]
    public async Task Scope_IsolatedAcrossConcurrentTasks()
    {
        // 16 concurrent tasks each set their own correlation ID; values
        // must not bleed between tasks. Sister to the Python
        // test_async_concurrent_dispatches_keep_correlation_ids_isolated case.
        var tasks = new Task[16];
        for (var i = 0; i < tasks.Length; i++)
        {
            var id = $"cid-{i}";
            tasks[i] = Task.Run(async () =>
            {
                using (CorrelationId.Scope(id))
                {
                    await Task.Yield();
                    Assert.Equal(id, CorrelationId.Current);
                    await Task.Delay(2);
                    Assert.Equal(id, CorrelationId.Current);
                }
            });
        }

        await Task.WhenAll(tasks);
    }

    [Fact]
    public void Scope_RejectsNullOrEmpty()
    {
        Assert.ThrowsAny<System.ArgumentException>(() => CorrelationId.Scope(null!));
        Assert.ThrowsAny<System.ArgumentException>(() => CorrelationId.Scope(""));
    }
}
