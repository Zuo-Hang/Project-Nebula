import { apiClient } from './client'

// 使用相对路径，通过 Vite 代理访问
const LLM_API_BASE_URL = import.meta.env.VITE_LLM_API_BASE_URL || '/api/llm'

export interface InferenceRequest {
  prompt: string
  imageUrl?: string | null
  ocrText?: string | null
  model?: string | null
}

export interface InferenceResponse {
  success: boolean
  content?: string
  error?: string
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  ocrText?: string // OCR识别结果（从后端返回）
}

export interface HealthResponse {
  status: string
  service: string
  serviceAvailable: boolean
  availableModels?: string[]
}

export interface ServiceInfo {
  service: string
  version: string
  status: string
  endpoints: {
    health: string
    infer: string
  }
}

export interface QualityInfo {
  width: number
  height: number
  resolution: string
  quality: string
}

export interface QualityResponse {
  success: boolean
  width?: number
  height?: number
  resolution?: string
  quality?: string
  error?: string
}

/**
 * 获取服务信息
 */
export const getServiceInfo = async (): Promise<ServiceInfo> => {
  const response = await fetch(`${LLM_API_BASE_URL}/`)
  return response.json()
}

/**
 * 健康检查
 */
export const checkHealth = async (): Promise<HealthResponse> => {
  const response = await fetch(`${LLM_API_BASE_URL}/health`)
  return response.json()
}

/**
 * 调用本地大模型进行推理
 */
export const infer = async (request: InferenceRequest): Promise<InferenceResponse> => {
  const response = await fetch(`${LLM_API_BASE_URL}/infer`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  return response.json()
}

/**
 * 获取文件清晰度信息
 */
export const getFileQuality = async (fileUrl: string): Promise<QualityResponse> => {
  const response = await fetch(`${LLM_API_BASE_URL}/quality?fileUrl=${encodeURIComponent(fileUrl)}`)
  return response.json()
}

