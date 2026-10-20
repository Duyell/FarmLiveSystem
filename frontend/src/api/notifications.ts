import { del, get, post, put, type PageResult } from './http'

export interface NotificationItem {
  id: number
  type: string
  channel: string
  title: string
  content: string
  status: string
  retryCount: number
  sentAt: string | null
  read: boolean
  createdAt: string
}

export interface NotificationTemplate {
  id: number
  code: string
  name: string
  type: string
  titleTemplate: string
  contentTemplate: string
  enabled: boolean
  updatedAt: string
}

export const NOTIFICATION_TYPE: Record<string, string> = {
  review: '审核结果',
  queue: '顺位变化',
  candidate: '候补提醒',
  reminder: '开播提醒',
  device_fault: '设备故障'
}

export const NOTIFICATION_STATUS: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  pending: { label: '待发送', type: 'warning' },
  sent: { label: '已发送', type: 'success' },
  failed: { label: '失败待重试', type: 'danger' },
  final_failed: { label: '最终失败', type: 'info' }
}

export async function listMyNotifications(params: {
  type?: string
  page?: number
  size?: number
}): Promise<PageResult<NotificationItem>> {
  return get<PageResult<NotificationItem>>('/notifications', params)
}

export async function markNotificationRead(id: number): Promise<void> {
  await post(`/notifications/${id}/read`)
}

export async function listAllNotifications(params: {
  type?: string
  channel?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResult<NotificationItem>> {
  return get<PageResult<NotificationItem>>('/live/notifications', params)
}

export async function retryNotification(id: number): Promise<void> {
  await post(`/live/notifications/${id}/retry`)
}

export async function listTemplates(type?: string): Promise<NotificationTemplate[]> {
  return get<NotificationTemplate[]>('/live/notification-templates', type ? { type } : undefined)
}

export async function createTemplate(payload: Omit<NotificationTemplate, 'id' | 'updatedAt'>): Promise<void> {
  await post('/live/notification-templates', payload)
}

export async function updateTemplate(id: number, payload: Omit<NotificationTemplate, 'id' | 'updatedAt'>): Promise<void> {
  await put(`/live/notification-templates/${id}`, payload)
}

export async function deleteTemplate(id: number): Promise<void> {
  await del(`/live/notification-templates/${id}`)
}
