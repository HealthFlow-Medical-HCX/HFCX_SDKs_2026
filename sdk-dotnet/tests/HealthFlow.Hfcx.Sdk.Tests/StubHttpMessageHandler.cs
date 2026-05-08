// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

namespace HealthFlow.Hfcx.Sdk.Tests;

/// <summary>
/// Test double for <see cref="HttpMessageHandler"/> that replays a queued
/// sequence of responses (or invokes a delegate per call). Sister to
/// <c>respx</c> on the Python side.
/// </summary>
internal sealed class StubHttpMessageHandler : HttpMessageHandler
{
    private readonly Queue<Func<HttpRequestMessage, Task<HttpResponseMessage>>> _responders
        = new();
    private readonly ConcurrentBag<RecordedCall> _calls = new();
    private int _callCount;

    public IReadOnlyCollection<RecordedCall> Calls => _calls;

    public int CallCount => Volatile.Read(ref _callCount);

    public sealed record RecordedCall(
        HttpMethod Method,
        Uri? RequestUri,
        string? Body,
        System.Net.Http.Headers.HttpRequestHeaders Headers);

    public StubHttpMessageHandler EnqueueJson(HttpStatusCode status, string body)
        => Enqueue(_ => Task.FromResult(new HttpResponseMessage(status)
        {
            Content = new StringContent(body, System.Text.Encoding.UTF8, "application/json"),
        }));

    public StubHttpMessageHandler EnqueueStatus(HttpStatusCode status, string body = "")
        => Enqueue(_ => Task.FromResult(new HttpResponseMessage(status)
        {
            Content = new StringContent(body),
        }));

    public StubHttpMessageHandler EnqueueException(Exception ex)
        => Enqueue(_ => Task.FromException<HttpResponseMessage>(ex));

    public StubHttpMessageHandler Enqueue(
        Func<HttpRequestMessage, Task<HttpResponseMessage>> responder)
    {
        lock (_responders)
        {
            _responders.Enqueue(responder);
        }

        return this;
    }

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        Interlocked.Increment(ref _callCount);

        string? body = null;
        if (request.Content is not null)
        {
            body = await request.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
        }

        _calls.Add(new RecordedCall(request.Method, request.RequestUri, body, request.Headers));

        Func<HttpRequestMessage, Task<HttpResponseMessage>> responder;
        lock (_responders)
        {
            if (_responders.Count == 0)
            {
                throw new InvalidOperationException(
                    $"No queued response for {request.Method} {request.RequestUri} (call #{_callCount})");
            }

            responder = _responders.Dequeue();
        }

        return await responder(request).ConfigureAwait(false);
    }
}
