// API Response Types based on FRONTEND_BACKEND_MAP.md

export interface ApiError {
  status: number;
  message: string;
  timestamp: string;
  correlationId: string;
}

// Authentication Types
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  refreshExpiresIn: number;
  scope: string;
  idToken: string;
  customerId: number;
  fullName: string;
}

export interface SignupRequest {
  username: string;
  fullName: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

// Customer Types
export interface CustomerResponse {
  id: number;
  username: string;
  fullName: string;
  kycStatus: string;
}

export interface UpdateCustomerRequest {
  fullName: string;
}

// Policy Types
export interface PolicyResponse {
  id: number;
  policyNumber: string;
  status: string;
  coveragePlanName: string;
  productType: string;
  effectiveDate: string;
  renewalDate: string;
}

export interface PolicyCreateRequest {
  coveragePlanId: number;
  effectiveDate: string;
  renewalDate?: string;
}

export interface PolicyUpdateRequest {
  status?: string;
  renewalDate?: string;
}

export interface PolicyCoverageDto {
  policyId: number;
  coverageDetails: string;
}

// Claim Types
export interface ClaimRequest {
  policyId: number;
  incidentType: string;
  incidentDate: string;
  estimatedAmountCents?: number;
}

export interface ClaimResponse {
  id: number;
  claimNumber: string;
  status: string;
  incidentType: string;
}

export interface ClaimSummaryResponse {
  id: number;
  claimNumber: string;
  policyId: number;
  incidentType: string;
  status: string;
  estimatedAmountCents?: number;
  approvedAmountCents?: number;
  role: string;
  incidentDate: string;
  createdAt: string;
}

export interface UpdateClaimStatusRequest {
  status: string;
  note?: string;
}

export interface ClaimStatusDto {
  claimId: number;
  policyId: number;
  claimNumber: string;
  status: string;
  incidentType: string;
  estimatedAmountCents?: number;
  approvedAmountCents?: number;
  history: StatusHistoryEntry[];
}

export interface StatusHistoryEntry {
  fromStatus: string;
  toStatus: string;
  changedBy: string;
  changedAt: string;
}

// Claim Document Types
export interface ClaimDocumentSummaryDto {
  documentId: number;
  docType: DocumentType;
  ocrStatus: OcrStatus;
  extractedText?: string;
  fraudSignalScore?: number;
}

export type DocumentType = 'PHOTO' | 'POLICE_REPORT' | 'MEDICAL_BILL' | 'REPAIR_ESTIMATE';
export type OcrStatus = 'PENDING' | 'COMPLETED';
export type ProcessingStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

// Claim Permission Types
export type ClaimPermission = 'VIEW' | 'SUBMIT_DOCUMENTS' | 'UPDATE_STATUS' | 'VIEW_PARTIES' | 'MANAGE_PARTIES' | 'VIEW_AUDIT_TRAIL';
export type ClaimRole = 'POLICYHOLDER' | 'ADJUSTER' | 'AUDITOR';

// Agent/AI Types
export interface AgentRequest {
  message: string;
  claimId: number;
}

export interface AgentMessageResponse {
  id: number;
  role: MessageRole;
  content: string;
  tokensUsed?: number;
  createdAt: string;
  events: AgentEventResponse[];
}

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

export interface AgentEventResponse {
  id: number;
  type: string;
  status: string;
  sequenceOrder: number;
  content?: string;
  sagaId?: string;
  proposedStatus?: string;
}

export interface StreamResponse {
  text: string;
  eventType: StreamEventType;
  requestId: string;
  done: boolean;
  errorCode?: string;
}

export type StreamEventType = 'message' | 'done' | 'error';

// Role Types
export type RealmRole = 'CUSTOMER' | 'ADJUSTER' | 'AUDITOR';
