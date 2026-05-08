// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using System.Text.Json;
using HealthFlow.Hfcx.Sdk.Exceptions;
using HealthFlow.Hfcx.Sdk.Protocol;
using HealthFlow.Hfcx.Sdk.Recipient;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

namespace HealthFlow.Hfcx.Sdk.Examples.RecipientAspNet;

/// <summary>
/// Composes a minimal-API ASP.NET Core app that wires
/// <see cref="RecipientHandler"/> into the five HFCX inbound endpoints.
/// Sister to Python's <c>recipient-fastapi</c> + <c>recipient-flask</c>
/// example apps and Java's <c>recipient-spring-boot-example</c>.
/// </summary>
/// <remarks>
/// EXAMPLE only — not a production-grade deployment. Plug a real
/// <see cref="ILocalKeyProvider"/> (file or Vault) and a real
/// <see cref="HealthFlow.Hfcx.Sdk.Auth.IBearerTokenValidator"/> backed
/// by a Keycloak JWKS endpoint before deploying.
/// </remarks>
public static class RecipientApp
{
    /// <summary>The five HFCX endpoints, in declaration order.</summary>
    public static readonly string[] HfcxPaths =
    [
        "/v1/coverageeligibility/check",
        "/v1/preauth/submit",
        "/v1/claim/submit",
        "/v1/communication/on_request",
        "/v1/paymentnotice/notify",
    ];

    /// <summary>
    /// Build a fully-configured <see cref="WebApplication"/> that
    /// dispatches all five HFCX endpoints through
    /// <paramref name="handler"/>.
    /// </summary>
    public static WebApplication BuildApp(RecipientHandler handler, string[]? args = null)
    {
        ArgumentNullException.ThrowIfNull(handler);

        var builder = WebApplication.CreateBuilder(args ?? []);
        builder.Services.AddSingleton(handler);

        var app = builder.Build();

        foreach (var path in HfcxPaths)
        {
            app.MapPost(path, Dispatch);
        }

        return app;
    }

    private static async Task<IResult> Dispatch(
        HttpRequest request,
        [FromServices] RecipientHandler handler)
    {
        // Read the JSON body once so RecipientHandler.Handle (sync) sees it.
        using var reader = new StreamReader(request.Body);
        var body = await reader.ReadToEndAsync().ConfigureAwait(false);

        var protocolHeaders = new Dictionary<string, string>
        {
            [ProtocolHeaders.SenderCode] = request.Headers[ProtocolHeaders.SenderCode].ToString(),
            [ProtocolHeaders.RecipientCode] = request.Headers[ProtocolHeaders.RecipientCode].ToString(),
            [ProtocolHeaders.CorrelationId] = request.Headers[ProtocolHeaders.CorrelationId].ToString(),
            [ProtocolHeaders.Timestamp] = request.Headers[ProtocolHeaders.Timestamp].ToString(),
            [ProtocolHeaders.ApiCallId] = request.Headers[ProtocolHeaders.ApiCallId].ToString(),
        };

        try
        {
            var authorization = request.Headers.Authorization.ToString();
            var result = handler.Handle(
                string.IsNullOrEmpty(authorization) ? null : authorization,
                protocolHeaders,
                body);

            return Results.Accepted(value: new
            {
                correlation_id = result.CorrelationId,
                status = "accepted",
            });
        }
        catch (AuthenticationException ex)
        {
            return ErrorResponse(StatusCodes.Status401Unauthorized, ex);
        }
        catch (ProtocolException ex)
        {
            return ErrorResponse(StatusCodes.Status400BadRequest, ex);
        }
        catch (BusinessException ex)
        {
            return ErrorResponse(StatusCodes.Status422UnprocessableEntity, ex);
        }
        catch (HfcxException ex)
        {
            return ErrorResponse(StatusCodes.Status500InternalServerError, ex);
        }
    }

    private static IResult ErrorResponse(int status, HfcxException ex)
    {
        var body = new
        {
            error = new
            {
                code = ex.Code,
                message = ex.Message ?? string.Empty,
            },
        };
        return Results.Json(body, statusCode: status);
    }
}
