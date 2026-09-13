// Claim statuses as defined by backend
export const CLAIM_STATUSES = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'DOCS_REQUESTED',
  'APPROVED',
  'DENIED',
  'PAID',
  'CLOSED',
] as const;

export type ClaimStatus = typeof CLAIM_STATUSES[number];

// Product types
export const PRODUCT_TYPES = [
  'Auto',
  'Home',
  'Health',
  'Travel',
  'Life',
] as const;

export type ProductType = typeof PRODUCT_TYPES[number];

// Policy statuses
export const POLICY_STATUSES = [
  'Active',
  'Inactive',
  'Pending',
  'Expired',
] as const;

export type PolicyStatus = typeof POLICY_STATUSES[number];
