import { get } from './http'

export interface UserInfo {
  id: number
  username: string
  displayName: string
  roleCode: string
  roleName: string
  farmerId: number | null
  farmerName: string | null
  mustChangePassword: boolean
}

export interface LoginResult {
  token: string
  user: UserInfo
}

export interface Captcha {
  captchaId: string
  question: string
}

export async function getCaptcha(): Promise<Captcha> {
  return get<Captcha>('/auth/captcha')
}

export function saveAuth(token: string, user: UserInfo) {
  localStorage.setItem('farm_token', token)
  localStorage.setItem('farm_user', JSON.stringify(user))
}

export function clearAuth() {
  localStorage.removeItem('farm_token')
  localStorage.removeItem('farm_user')
}

export function currentUser(): UserInfo | null {
  const raw = localStorage.getItem('farm_user')
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export function token(): string | null {
  return localStorage.getItem('farm_token')
}
