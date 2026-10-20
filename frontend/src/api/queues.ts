import { get, post } from './http'
import type { Booking } from './bookings'

export interface QueueTicket {
  id: number
  queueNo: string
  farmerId: number
  farmerName: string
  venueId: number
  venueName: string
  slotStart: string
  slotEnd: string
  position: number
  totalInQueue: number
  estimatedWait: number
  status: string
  candidateDeadline: string | null
  assignedDeviceId: number | null
  assignedDeviceName: string | null
  confirmedBookingId: number | null
  createdAt: string
}

export interface SlotStatus {
  occupied: boolean
  selfOccupied: boolean
  canJoin: boolean
  queueCount: number
  reason: string
}

export async function slotStatus(params: {
  venueId: number
  startTime: string
  endTime: string
}): Promise<SlotStatus> {
  return get<SlotStatus>('/farmer/queues/slot-status', params)
}

export const QUEUE_STATUS: Record<string, { label: string; type: 'warning' | 'success' | 'danger' | 'info' | 'primary' }> = {
  queued: { label: '排队中', type: 'info' },
  candidate: { label: '候补状态', type: 'warning' },
  confirmed: { label: '已确认', type: 'success' },
  expired: { label: '已超时', type: 'danger' },
  abandoned: { label: '已放弃', type: 'info' },
  closed: { label: '已关闭', type: 'info' }
}

export async function joinQueue(payload: {
  venueId: number
  startTime: string
  endTime: string
}): Promise<QueueTicket> {
  return post<QueueTicket>('/farmer/queues', payload)
}

export async function listMyQueues(): Promise<QueueTicket[]> {
  return get<QueueTicket[]>('/farmer/queues')
}

export async function confirmQueue(id: number): Promise<{ confirmedBookingId: number | null }> {
  return post(`/farmer/queues/${id}/confirm`)
}

export async function abandonQueue(id: number): Promise<QueueTicket> {
  return post<QueueTicket>(`/farmer/queues/${id}/abandon`)
}

export async function listLiveQueues(params: {
  venueId?: number
  date?: string
}): Promise<QueueTicket[]> {
  return get<QueueTicket[]>('/live/queues', params)
}

export async function closeQueue(id: number): Promise<QueueTicket> {
  return post<QueueTicket>(`/live/queues/${id}/close`)
}

export type { Booking }
