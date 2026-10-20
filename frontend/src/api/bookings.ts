import { get, post, type PageResult } from './http'

export interface BookingProduct {
  productName: string
  spec: string | null
  quantity: number
  unit: string
}

export interface Booking {
  id: number
  bookingNo: string
  farmerId: number
  farmerName: string
  venueId: number
  venueName: string
  deviceId: number
  deviceNo: string
  deviceName: string
  startTime: string
  endTime: string
  topic: string
  status: string
  reviewComment: string | null
  recommendedTime: string | null
  cancelReason: string | null
  products: BookingProduct[]
  createdAt: string
}

export interface AvailableDevice {
  deviceId: number
  deviceNo: string
  name: string
}

export interface AvailableVenue {
  venueId: number
  venueNo: string
  venueName: string
  countyCode: string | null
  openStart: string
  openEnd: string
  devices: AvailableDevice[]
}

export const BOOKING_STATUS: Record<string, { label: string; type: 'warning' | 'success' | 'danger' | 'info' }> = {
  pending_review: { label: '待审核', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  canceled: { label: '已取消', type: 'info' },
  completed: { label: '已完成', type: 'success' },
  no_show: { label: '未到场', type: 'info' }
}

export interface BookingCreatePayload {
  venueId: number
  deviceId: number
  startTime: string
  endTime: string
  topic: string
  products: { productName: string; spec: string | null }[]
}

export async function submitBooking(payload: BookingCreatePayload): Promise<Booking> {
  return post<Booking>('/farmer/bookings', payload)
}

export async function listMyBookings(params: {
  status?: string
  page?: number
  size?: number
}): Promise<PageResult<Booking>> {
  return get<PageResult<Booking>>('/farmer/bookings', params)
}

export async function cancelBooking(id: number, reason?: string): Promise<Booking> {
  return post<Booking>(`/farmer/bookings/${id}/cancel`, { reason })
}

export async function listAvailableVenues(params: {
  countyCode?: string
  date?: string
  startTime?: string
  endTime?: string
}): Promise<AvailableVenue[]> {
  return get<AvailableVenue[]>('/resources/available', params)
}

export async function listLiveBookings(params: {
  status?: string
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResult<Booking>> {
  return get<PageResult<Booking>>('/live/bookings', params)
}

export async function reviewBooking(
  id: number,
  payload: { result: 'approve' | 'reject'; comment?: string; recommendedTime?: string }
): Promise<Booking> {
  return post<Booking>(`/live/bookings/${id}/review`, payload)
}

export async function completeBooking(
  id: number,
  status: 'completed' | 'no_show'
): Promise<Booking> {
  return post<Booking>(`/live/bookings/${id}/complete`, { status })
}
