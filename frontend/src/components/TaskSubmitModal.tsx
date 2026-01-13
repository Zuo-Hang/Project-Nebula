import { useState } from 'react'
import { Modal, Form, Input, Button, message } from 'antd'
import { submitTask, TaskSubmitRequest } from '../api/task'

interface TaskSubmitModalProps {
  visible: boolean
  onCancel: () => void
  onSuccess: () => void
}

const TaskSubmitModal = ({ visible, onCancel, onSuccess }: TaskSubmitModalProps) => {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setLoading(true)
      await submitTask(values as TaskSubmitRequest)
      form.resetFields()
      onSuccess()
    } catch (error: any) {
      if (error.errorFields) {
        // 表单验证错误
        return
      }
      message.error('提交任务失败: ' + (error.message || '未知错误'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      title="提交任务"
      open={visible}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" onClick={onCancel}>
          取消
        </Button>,
        <Button key="submit" type="primary" loading={loading} onClick={handleSubmit}>
          提交
        </Button>,
      ]}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="taskType"
          label="任务类型"
          rules={[{ required: true, message: '请输入任务类型' }]}
        >
          <Input placeholder="例如: video-processing" />
        </Form.Item>
        <Form.Item name="videoKey" label="视频Key（S3）">
          <Input placeholder="例如: videos/example.mp4" />
        </Form.Item>
        <Form.Item name="linkName" label="链接名称">
          <Input placeholder="例如: example-link" />
        </Form.Item>
        <Form.Item name="submitDate" label="提交日期">
          <Input placeholder="例如: 2024-01-01" />
        </Form.Item>
        <Form.Item name="imageUrl" label="图片URL">
          <Input placeholder="例如: https://example.com/image.jpg" />
        </Form.Item>
        <Form.Item name="videoPath" label="视频路径">
          <Input placeholder="例如: /path/to/video.mp4" />
        </Form.Item>
        <Form.Item name="prompt" label="提示词">
          <Input.TextArea rows={4} placeholder="输入LLM推理的提示词" />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default TaskSubmitModal

