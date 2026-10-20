import { del, get, post, put, type PageResult } from './http'

export interface SalesRecord {
  id: number
  bookingId: number
  bookingNo: string
  farmerName: string
  venueName: string
  startTime: string
  productName: string
  spec: string | null
  quantity: number
  unit: string
  createdAt: string
  updatedAt: string
}

export interface SalesPayload {
  bookingId: number
  productName: string
  spec: string | null
  quantity: number
  unit: string
}

export async function listSales(params: {
  bookingId?: number
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResult<SalesRecord>> {
  return get<PageResult<SalesRecord>>('/live/sales', params)
}

export async function createSales(payload: SalesPayload): Promise<SalesRecord> {
  return post<SalesRecord>('/live/sales', payload)
}

export async function updateSales(id: number, payload: SalesPayload): Promise<SalesRecord> {
  return put<SalesRecord>(`/live/sales/${id}`, payload)
}

export async function deleteSales(id: number): Promise<void> {
  return del<void>(`/live/sales/${id}`)
}
