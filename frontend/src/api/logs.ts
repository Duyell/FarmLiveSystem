import { download, get, type PageResult } from './http'

export interface AuditLog {
  id: number
  operatorId: number | null
  operatorName: string
  roleCode: string
  action: string
  detail: string | null
  ip: string | null
  createdAt: string
}

export interface AuditQuery extends Record<string, unknown> {
  keyword?: string
  action?: string
  from?: string
  to?: string
}

export async function listLogs(
  params: AuditQuery & { page?: number; size?: number }
): Promise<PageResult<AuditLog>> {
  return get<PageResult<AuditLog>>('/admin/logs', params)
}

export async function downloadLogs(params: AuditQuery): Promise<Blob> {
  return download('/admin/logs/export', params)
}
