import axios from 'axios'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('farm_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '请求失败'
    if (status === 401) {
      localStorage.removeItem('farm_token')
      localStorage.removeItem('farm_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(new Error(message))
  }
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = (await http.get(url, { params })) as unknown as ApiResponse<T>
  return response.data
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const response = (await http.post(url, data)) as unknown as ApiResponse<T>
  return response.data
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  const response = (await http.put(url, data)) as unknown as ApiResponse<T>
  return response.data
}

export async function del<T>(url: string): Promise<T> {
  const response = (await http.delete(url)) as unknown as ApiResponse<T>
  return response.data
}

export async function download(url: string, params?: Record<string, unknown>): Promise<Blob> {
  const response = await http.get(url, { params, responseType: 'blob' })
  return response as unknown as Blob
}

export default http
