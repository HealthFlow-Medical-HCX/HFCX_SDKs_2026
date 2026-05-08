// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

using HealthFlow.Hfcx.Sdk.Examples.RecipientAspNet;
using HealthFlow.Hfcx.Sdk.Recipient;

// Production deployments construct a real RecipientHandler with a
// FileLocalKeyProvider (or VaultLocalKeyProvider) and a JWKS-backed
// IBearerTokenValidator. This entry point is left intentionally minimal
// — see the integration tests for an end-to-end wiring example with an
// in-memory key.

if (args.Length < 2)
{
    Console.Error.WriteLine("usage: RecipientAspNetExample <key.pem> <local-participant-code>");
    return 1;
}

var keyProvider = new FileLocalKeyProvider(args[0]);
var localParticipantCode = args[1];

var handler = new RecipientHandler(
    keyProvider: keyProvider,
    localParticipantCode: localParticipantCode,
    // Wire a JWKS-backed validator here in production.
    enabledLayers: new HashSet<Layer> { Layer.Headers, Layer.Fhir, Layer.Egyptian });

var app = RecipientApp.BuildApp(handler, args[2..]);
app.Run();
return 0;
