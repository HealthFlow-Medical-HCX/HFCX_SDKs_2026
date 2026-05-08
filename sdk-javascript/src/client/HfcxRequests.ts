// Copyright (c) HealthFlow Medical HCX. Licensed under Apache 2.0.

/**
 * Outcome of an HFCX outbound request as observed at the SDK call site.
 * Cross-SDK invariant with Java's `Status` enum, Python's `Status`
 * enum, and .NET's `Status` enum.
 */
export type Status = 'ACCEPTED' | 'REJECTED' | 'STUBBED';

export const Status = {
  ACCEPTED: 'ACCEPTED',
  REJECTED: 'REJECTED',
  STUBBED: 'STUBBED',
} as const satisfies Record<Status, Status>;

/**
 * Closed set of outbound operations the SDK supports. Cross-SDK
 * invariant with the other SDKs' `Operation` enums.
 */
export type Operation =
  | 'CHECK_ELIGIBILITY'
  | 'SUBMIT_PREAUTH'
  | 'SUBMIT_CLAIM'
  | 'SEND_COMMUNICATION'
  | 'NOTIFY_PAYMENT';

export const Operation = {
  CHECK_ELIGIBILITY: 'CHECK_ELIGIBILITY',
  SUBMIT_PREAUTH: 'SUBMIT_PREAUTH',
  SUBMIT_CLAIM: 'SUBMIT_CLAIM',
  SEND_COMMUNICATION: 'SEND_COMMUNICATION',
  NOTIFY_PAYMENT: 'NOTIFY_PAYMENT',
} as const satisfies Record<Operation, Operation>;

/**
 * Default endpoint paths per Integration Guide §22. Override via
 * `HfcxClient`'s `endpoints` option.
 */
export const DEFAULT_ENDPOINTS: Readonly<Record<Operation, string>> = {
  CHECK_ELIGIBILITY: '/v1/coverageeligibility/check',
  SUBMIT_PREAUTH: '/v1/preauth/submit',
  SUBMIT_CLAIM: '/v1/claim/submit',
  SEND_COMMUNICATION: '/v1/communication/on_request',
  NOTIFY_PAYMENT: '/v1/paymentnotice/notify',
};

/** Common return shape for every `HfcxClient` sender method. */
export interface HfcxResponse {
  readonly correlationId: string;
  readonly status: Status;
}

/** Sealed marker for the five HFCX outbound request types. */
export type HfcxRequest =
  | CheckEligibilityRequest
  | SubmitPreauthRequest
  | SubmitClaimRequest
  | SendCommunicationRequest
  | NotifyPaymentRequest;

export interface CheckEligibilityRequest {
  readonly recipientCode: string;
  readonly eligibilityBundle: string;
  readonly correlationId?: string;
}

export interface SubmitPreauthRequest {
  readonly recipientCode: string;
  readonly preauthBundle: string;
  readonly correlationId?: string;
}

export interface SubmitClaimRequest {
  readonly recipientCode: string;
  readonly claimBundle: string;
  readonly correlationId?: string;
}

export interface SendCommunicationRequest {
  readonly recipientCode: string;
  readonly communicationBundle: string;
  readonly correlationId?: string;
}

export interface NotifyPaymentRequest {
  readonly recipientCode: string;
  readonly paymentNoticeBundle: string;
  readonly correlationId?: string;
}
