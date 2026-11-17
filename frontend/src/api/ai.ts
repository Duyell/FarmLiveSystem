import { del, get, post, put, type PageResult } from './http'
import { token } from './auth'

export interface KnowledgeDoc {
  id: number
  title: string
  fileName: string
  fileType: string
  fileSize: number
  parseStatus: string
  enabled: boolean
  chunkCount: number
  uploadedByName: string | null
  createdAt: string
  updatedAt: string
}

export const PARSE_STATUS: Record<string, { label: string; type: 'success' | 'danger' | 'warning' | 'info' }> = {
  pending: { label: '待解析', type: 'warning' },
  parsed: { label: '已解析', type: 'success' },
  failed: { label: '解析失败', type: 'danger' }
}

export async function listKnowledgeDocs(): Promise<KnowledgeDoc[]> {
  return get<KnowledgeDoc[]>('/knowledge/docs')
}

export async function uploadKnowledgeDoc(
  file: File,
  title?: string
): Promise<KnowledgeDoc> {
  const form = new FormData()
  form.append('file', file)
  if (title) form.append('title', title)
  return post<KnowledgeDoc>('/knowledge/docs', form)
}

export async function reparseKnowledgeDoc(id: number): Promise<KnowledgeDoc> {
  return post<KnowledgeDoc>(`/knowledge/docs/${id}/reparse`)
}

export async function toggleKnowledgeDoc(id: number, enabled: boolean): Promise<KnowledgeDoc> {
  return put<KnowledgeDoc>(`/knowledge/docs/${id}/toggle`, { enabled })
}

export async function deleteKnowledgeDoc(id: number): Promise<void> {
  await del(`/knowledge/docs/${id}`)
}

export interface QaRecord {
  id: number
  question: string
  answer: string
  citations: string[]
  createdAt: string
}

export async function askQuestion(question: string): Promise<QaRecord> {
  return post<QaRecord>('/qa/ask', { question }, { timeout: 90000 })
}

export interface StreamResult {
  recordId: number
  citations: string[]
}

/**
 * 流式问答：逐片段回调 onDelta，结束时返回记录 ID 与引用来源。
 */
export async function askQuestionStream(
  question: string,
  onDelta: (text: string) => void
): Promise<StreamResult> {
  const response = await fetch('/api/qa/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token() || ''}`
    },
    body: JSON.stringify({ question })
  })
  if (!response.ok) {
    let message = `请求失败（${response.status}）`
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      // 忽略非 JSON 错误体
    }
    throw new Error(message)
  }
  if (!response.body) throw new Error('浏览器不支持流式响应')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let result: StreamResult = { recordId: 0, citations: [] }
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    let index: number
    while ((index = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      const line = raw.split('\n').find((item) => item.startsWith('data:'))
      if (!line) continue
      try {
        const data = JSON.parse(line.slice(5).trim())
        if (typeof data.delta === 'string' && data.delta) onDelta(data.delta)
        if (data.done) result = { recordId: data.recordId, citations: data.citations || [] }
      } catch {
        // 忽略格式异常的片段
      }
    }
  }
  return result
}

export async function listQaHistory(params: {
  page?: number
  size?: number
}): Promise<PageResult<QaRecord>> {
  return get<PageResult<QaRecord>>('/qa/records', params)
}

export interface VerifyToken {
  verifyToken: string
  expiresInSeconds: number
}

export async function verifyPassword(password: string): Promise<VerifyToken> {
  return post<VerifyToken>('/auth/verify-password', { password })
}

export interface FarmerPhone {
  id: number
  farmerNo: string
  name: string
  phone: string
}

export async function getFarmerPhone(id: number, verifyToken: string): Promise<FarmerPhone> {
  return get<FarmerPhone>(`/admin/farmers/${id}/phone`, { verifyToken })
}
