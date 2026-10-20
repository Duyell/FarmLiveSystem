import http, { download, get, post, type ApiResponse } from './http'

export interface Qualification {
  id: number
  farmerId: number
  originalName: string
  contentType: string | null
  fileSize: number
  status: string
  reviewComment: string | null
  uploaderName: string | null
  createdAt: string
  reviewedAt: string | null
}

export const QUALIFICATION_STATUS: Record<string, { label: string; type: 'warning' | 'success' | 'danger' }> = {
  pending: { label: '待审核', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' }
}

export async function listQualifications(farmerId: number): Promise<Qualification[]> {
  return get<Qualification[]>(`/live/farmers/${farmerId}/qualifications`)
}

export async function uploadQualification(farmerId: number, file: File): Promise<Qualification> {
  const form = new FormData()
  form.append('file', file)
  const response = (await http.post(`/live/farmers/${farmerId}/qualifications`, form)) as unknown as ApiResponse<Qualification>
  return response.data
}

export async function reviewQualification(
  id: number,
  payload: { result: 'approve' | 'reject'; comment?: string }
): Promise<Qualification> {
  return post<Qualification>(`/live/qualifications/${id}/review`, payload)
}

export async function downloadQualification(id: number): Promise<Blob> {
  return download(`/live/qualifications/${id}/file`)
}
