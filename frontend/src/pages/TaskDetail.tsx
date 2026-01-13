import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Card, Descriptions, Tag, Spin, Alert, Button } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getTaskDetail, TaskStatus } from '../api/task'
import dayjs from 'dayjs'

const TaskDetail = () => {
  const { taskId } = useParams<{ taskId: string }>()
  const navigate = useNavigate()

  const { data, isLoading, error } = useQuery({
    queryKey: ['task', taskId],
    queryFn: () => getTaskDetail(taskId!),
    enabled: !!taskId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      // 如果任务还在运行中，每5秒刷新一次
      return status === 'RUNNING' || status === 'PENDING' ? 5000 : false
    },
  })

  const getStatusTag = (status: string) => {
    const statusMap: Record<string, { color: string; text: string }> = {
      PENDING: { color: 'default', text: '待处理' },
      RUNNING: { color: 'processing', text: '运行中' },
      COMPLETED: { color: 'success', text: '已完成' },
      FAILED: { color: 'error', text: '失败' },
      CANCELLED: { color: 'warning', text: '已取消' },
    }
    const config = statusMap[status] || { color: 'default', text: status }
    return <Tag color={config.color}>{config.text}</Tag>
  }

  if (isLoading) {
    return (
      <div style={{ textAlign: 'center', padding: '50px' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (error) {
    return (
      <Alert
        message="加载失败"
        description="无法加载任务详情，请稍后重试"
        type="error"
        showIcon
      />
    )
  }

  const task = data as TaskStatus

  return (
    <div>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/')}
        style={{ marginBottom: 16 }}
      >
        返回列表
      </Button>

      <Card title="任务详情">
        <Descriptions column={2} bordered>
          <Descriptions.Item label="任务ID">{task.taskId}</Descriptions.Item>
          <Descriptions.Item label="状态">{getStatusTag(task.status)}</Descriptions.Item>
          <Descriptions.Item label="任务类型">{task.taskType || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {task.createdAt ? dayjs(task.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {task.updatedAt ? dayjs(task.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="进度">
            {task.progress !== undefined ? `${task.progress}%` : '-'}
          </Descriptions.Item>
          {task.errorMessage && (
            <Descriptions.Item label="错误信息" span={2}>
              <Alert message={task.errorMessage} type="error" showIcon />
            </Descriptions.Item>
          )}
          {task.result && (
            <Descriptions.Item label="结果" span={2}>
              <pre style={{ background: '#f5f5f5', padding: '12px', borderRadius: '4px', overflow: 'auto' }}>
                {JSON.stringify(task.result, null, 2)}
              </pre>
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>
    </div>
  )
}

export default TaskDetail

