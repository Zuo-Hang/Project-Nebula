import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Table, Button, Space, Tag, Card, Form, Input, Select, message } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getTaskList, TaskStatus } from '../api/task'
import TaskSubmitModal from '../components/TaskSubmitModal'
import dayjs from 'dayjs'

const { Option } = Select

const TaskList = () => {
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [submitModalVisible, setSubmitModalVisible] = useState(false)
  const [filters, setFilters] = useState<{ status?: string; taskType?: string }>({})

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['tasks', filters],
    queryFn: () => getTaskList({ ...filters, page: 1, pageSize: 20 }),
  })

  const handleFilter = (values: any) => {
    setFilters(values)
  }

  const handleReset = () => {
    form.resetFields()
    setFilters({})
  }

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

  const columns = [
    {
      title: '任务ID',
      dataIndex: 'taskId',
      key: 'taskId',
      ellipsis: true,
      render: (text: string) => (
        <a onClick={() => navigate(`/tasks/${text}`)}>{text}</a>
      ),
    },
    {
      title: '任务类型',
      dataIndex: 'taskType',
      key: 'taskType',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => getStatusTag(status),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text: string) => text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (text: string) => text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: TaskStatus) => (
        <Space>
          <Button type="link" onClick={() => navigate(`/tasks/${record.taskId}`)}>
            查看详情
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Card>
        <Form
          form={form}
          layout="inline"
          onFinish={handleFilter}
          style={{ marginBottom: 16 }}
        >
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择状态" allowClear style={{ width: 150 }}>
              <Option value="PENDING">待处理</Option>
              <Option value="RUNNING">运行中</Option>
              <Option value="COMPLETED">已完成</Option>
              <Option value="FAILED">失败</Option>
              <Option value="CANCELLED">已取消</Option>
            </Select>
          </Form.Item>
          <Form.Item name="taskType" label="任务类型">
            <Input placeholder="请输入任务类型" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Space style={{ marginBottom: 16 }}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setSubmitModalVisible(true)}
          >
            提交任务
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
            刷新
          </Button>
        </Space>

        <Table
          columns={columns}
          dataSource={data?.tasks || []}
          loading={isLoading}
          rowKey="taskId"
          pagination={{
            total: data?.total || 0,
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
        />
      </Card>

      <TaskSubmitModal
        visible={submitModalVisible}
        onCancel={() => setSubmitModalVisible(false)}
        onSuccess={() => {
          setSubmitModalVisible(false)
          refetch()
          message.success('任务提交成功')
        }}
      />
    </div>
  )
}

export default TaskList

