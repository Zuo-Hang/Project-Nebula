import { apiClient } from './client'

export interface TaskSubmitRequest {
  videoKey?: string
  linkName?: string
  submitDate?: string
  taskType?: string
  imageUrl?: string
  videoPath?: string
  prompt?: string
  customData?: Record<string, any>
}

export interface TaskStatus {
  taskId: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  taskType?: string
  createdAt?: string
  updatedAt?: string
  progress?: number
  errorMessage?: string
  result?: any
}

export interface TaskListResponse {
  tasks: TaskStatus[]
  total: number
  page: number
  pageSize: number
}

// 提交任务
export const submitTask = async (request: TaskSubmitRequest): Promise<TaskStatus> => {
  return apiClient.post('/tasks', request)
}

// 查询任务列表
export const getTaskList = async (params?: {
  page?: number
  pageSize?: number
  status?: string
  taskType?: string
}): Promise<TaskListResponse> => {
  return apiClient.get('/tasks', { params })
}

// 查询任务详情
export const getTaskDetail = async (taskId: string): Promise<TaskStatus> => {
  return apiClient.get(`/tasks/${taskId}`)
}

// 取消任务
export const cancelTask = async (taskId: string): Promise<void> => {
  return apiClient.post(`/tasks/${taskId}/cancel`)
}

