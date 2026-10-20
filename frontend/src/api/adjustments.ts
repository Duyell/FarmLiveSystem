import { get, post, type PageResult } from './http'

export interface Adjustment {
  id: number
  requestNo: string
  bookingId: number
  bookingNo: string
  farmerId: number
  farmerName: string
  oldVenueId: number
  oldVenueName: string
  oldStartTime: string
  oldEndTime: string
  newVenueId: number
  newVenueName: string
  newDeviceId: number
  newDeviceNo: string
  newDeviceName: string
  newStartTime: string
  newEndTime: string
  reason: string
  status: string
  reviewComment: string | null
  createdAt: string
  reviewedAt: string | null
}

export const ADJUSTMENT_STATUS: Record<string, { label: string; type: 'warning' | 'success' | 'danger' | 'info' }> = {
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  withdrawn: { label: '已撤回', type: 'info' }
}

export async function submitAdjustment(
  bookingId: number,
  payload: {
    newVenueId: number
    newDeviceId: number
    newStartTime: string
    newEndTime: string
    reason: string
  }
): Promise<Adjustment> {
  return post<Adjustment>(`/farmer/bookings/${bookingId}/adjustments`, payload)
}

export async function listMyAdjustments(params: {
  status?: string
  page?: number
  size?: number
}): Promise<PageResult<Adjustment>> {
  return get<PageResult<Adjustment>>('/farmer/adjustments', params)
}

export async function withdrawAdjustment(id: number): Promise<Adjustment> {
  return post<Adjustment>(`/farmer/adjustments/${id}/withdraw`)
}

export async function listLiveAdjustments(params: {
  status?: string
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResult<Adjustment>> {
  return get<PageResult<Adjustment>>('/live/adjustments', params)
}

export async function reviewAdjustment(
  id: number,
  payload: { result: 'approve' | 'reject'; comment?: string }
): Promise<Adjustment> {
  return post<Adjustment>(`/live/adjustments/${id}/review`, payload)
}
