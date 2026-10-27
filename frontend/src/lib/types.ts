export type Role = "CITIZEN" | "OFFICER" | "ADMIN";

export type ComplaintStatus =
  | "SUBMITTED"
  | "ACKNOWLEDGED"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "NEEDS_INFORMATION"
  | "RESOLVED"
  | "CLOSED"
  | "REJECTED";

export type Priority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface User {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  role: Role;
  departmentId?: number;
  departmentName?: string;
  address?: string;
  active: boolean;
  createdAt: string;
  lastLoginAt?: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PersonRef {
  id: number;
  name: string;
}

export interface Department {
  id: number;
  code: string;
  name: string;
  description?: string;
  contactEmail?: string;
  active: boolean;
}

export interface Category {
  id: number;
  name: string;
  description?: string;
  departmentId: number;
  departmentName: string;
  slaHours: number;
  defaultPriority: Priority;
  active: boolean;
}

export interface ComplaintSummary {
  id: number;
  reference: string;
  title: string;
  status: ComplaintStatus;
  priority: Priority;
  categoryId: number;
  categoryName: string;
  departmentId: number;
  departmentName: string;
  citizen?: PersonRef;
  assignedOfficer?: PersonRef;
  locationCity?: string;
  createdAt: string;
  updatedAt: string;
  dueAt: string;
  overdue: boolean;
  escalationLevel: number;
}

export interface AllowedActions {
  statusTargets: ComplaintStatus[];
  canAssign: boolean;
  canClaim: boolean;
  canChangePriority: boolean;
  canReroute: boolean;
  canComment: boolean;
  canPostInternal: boolean;
  canUpload: boolean;
  canReopen: boolean;
  canConfirmClose: boolean;
}

export interface Attachment {
  id: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string;
  createdAt: string;
}

export interface ComplaintDetail extends Omit<ComplaintSummary, "locationCity"> {
  description: string;
  citizenEmail?: string;
  citizenPhone?: string;
  locationAddress?: string;
  locationWard?: string;
  locationCity?: string;
  locationPincode?: string;
  latitude?: number;
  longitude?: number;
  resolutionNotes?: string;
  reopenCount: number;
  escalatedAt?: string;
  assignedAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  attachments: Attachment[];
  actions: AllowedActions;
}

export interface HistoryEntry {
  id: number;
  fromStatus?: ComplaintStatus;
  toStatus: ComplaintStatus;
  remark?: string;
  actorName: string;
  actorRole: string;
  createdAt: string;
}

export interface CommentItem {
  id: number;
  body: string;
  visibility: "PUBLIC" | "INTERNAL";
  authorName: string;
  authorRole: Role;
  mine: boolean;
  createdAt: string;
}

export interface NotificationItem {
  id: number;
  type: string;
  title: string;
  message: string;
  complaintId?: number;
  complaintReference?: string;
  readAt?: string;
  createdAt: string;
}

export interface Totals {
  total: number;
  open: number;
  overdue: number;
  resolved: number;
  closed: number;
  rejected: number;
  needsInformation: number;
  unassigned: number;
  escalated: number;
}

export interface Sla {
  resolvedCount: number;
  resolvedWithinSla: number;
  compliancePercent: number | null;
  avgResolutionHours: number | null;
  medianResolutionHours: number | null;
}

export interface CountItem {
  key: string;
  count: number;
}

export interface TrendPoint {
  date: string;
  submitted: number;
  resolved: number;
}

export interface DepartmentRow {
  departmentId: number;
  department: string;
  total: number;
  open: number;
  overdue: number;
  resolved: number;
  compliancePercent: number | null;
  avgResolutionHours: number | null;
}

export interface OfficerRow {
  officerId: number;
  officer: string;
  department?: string;
  open: number;
  overdue: number;
  resolvedLast30: number;
  avgResolutionHours: number | null;
}

export interface AdminOverview {
  totals: Totals;
  sla: Sla;
  byStatus: CountItem[];
  byPriority: CountItem[];
  trend: TrendPoint[];
  departments: DepartmentRow[];
  officers: OfficerRow[];
}

export interface OfficerDashboard {
  mine: Totals;
  mySla: Sla;
  byStatus: CountItem[];
  departmentUnassigned: number;
  departmentOverdue: number;
  trend: TrendPoint[];
}

export interface CitizenDashboard {
  totals: Totals;
  byStatus: CountItem[];
}

export interface CategorySlaRow {
  categoryId: number;
  category: string;
  department: string;
  slaHours: number;
  total: number;
  resolved: number;
  resolvedWithinSla: number;
  compliancePercent: number | null;
  avgResolutionHours: number | null;
  overdueOpen: number;
}

export interface PublicStatus {
  reference: string;
  categoryName: string;
  departmentName: string;
  status: ComplaintStatus;
  submittedAt: string;
  lastUpdatedAt: string;
  resolvedAt?: string;
  timeline: { status: ComplaintStatus; at: string }[];
}

export interface AuditLogItem {
  id: number;
  actorId?: number;
  actorEmail?: string;
  action: string;
  entityType: string;
  entityId?: string;
  details?: string;
  ipAddress?: string;
  createdAt: string;
}

export interface Setting {
  key: string;
  value: string;
  description?: string;
  updatedAt: string;
}

export interface OfficerOption {
  id: number;
  fullName: string;
  departmentId?: number;
  departmentName?: string;
}
