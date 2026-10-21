import { download, get, type PageResult } from './http'

export interface StatsOverview {
  occupancyRate: number
  liveCount: number
  soldQuantity: number
  successRate: number
  bookingTotal: number
  approvedCount: number
  completedCount: number
}

export interface StatsDetail {
  bookingId: number
  bookingNo: string
  farmerName: string
  venueName: string
  countyCode: string | null
  startTime: string
  topic: string
  soldQuantity: number
  salesCount: number
}

export interface MyStats {
  bookingTotal: number
  approvedCount: number
  completedCount: number
  soldQuantity: number
}

export interface StatsTrend {
  period: string
  liveCount: number
  soldQuantity: number
}

export interface StatsQuery extends Record<string, unknown> {
  from?: string
  to?: string
  countyCode?: string
  venueId?: number
  productName?: string
}

export async function getOverview(params: StatsQuery): Promise<StatsOverview> {
  return get<StatsOverview>('/stats/overview', params)
}

export async function getDetails(
  params: StatsQuery & { page?: number; size?: number }
): Promise<PageResult<StatsDetail>> {
  return get<PageResult<StatsDetail>>('/stats/details', params)
}

export async function getTrend(
  params: StatsQuery & { granularity?: 'day' | 'week' | 'month' }
): Promise<StatsTrend[]> {
  return get<StatsTrend[]>('/stats/trend', params)
}

export async function listProductNames(): Promise<string[]> {
  return get<string[]>('/stats/products')
}

export async function getMyStats(): Promise<MyStats> {
  return get<MyStats>('/farmer/stats')
}

export async function getMyStatsDetails(): Promise<StatsDetail[]> {
  return get<StatsDetail[]>('/farmer/stats/details')
}

export async function downloadStats(params: StatsQuery): Promise<Blob> {
  return download('/stats/export', params)
}
