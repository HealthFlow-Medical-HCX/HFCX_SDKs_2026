// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/** Outcome of a successful `RecipientHandler.handle` call. */
export interface RecipientResult {
  readonly decryptedPayload: string;
  readonly protocolHeaders: Readonly<Record<string, string>>;
  readonly correlationId: string;
}
