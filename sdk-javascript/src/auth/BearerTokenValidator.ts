// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * Validates an inbound `Authorization` header on the recipient side.
 * The SDK does NOT ship a default trust-everything implementation by
 * design — participants make a deliberate choice when wiring this up.
 *
 * Sister to Java's `BearerTokenValidator` `@FunctionalInterface`,
 * Python's `BearerTokenValidator` Protocol, and .NET's
 * `IBearerTokenValidator`.
 */
export interface BearerTokenValidator {
  /**
   * Validate the raw `Authorization` header value (typically
   * `"Bearer eyJ..."`). Throw `AuthenticationError` on rejection.
   */
  validate(authorizationHeader: string | null | undefined): void;
}
