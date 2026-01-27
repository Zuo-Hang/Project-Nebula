import { useState, useEffect } from 'react'
import {
  Card,
  Input,
  Button,
  Space,
  Typography,
  Alert,
  Spin,
  Tag,
  Divider,
  Row,
  Col,
  Select,
  Upload,
  message,
} from 'antd'
import {
  SendOutlined,
  ClearOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  UploadOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import type { UploadFile } from 'antd'
import { infer, checkHealth, getServiceInfo, getFileQuality, InferenceRequest, QualityInfo } from '../api/llm'
import './LocalLLM.css'

const { TextArea } = Input
const { Title, Text } = Typography

const LocalLLM = () => {
  const [prompt, setPrompt] = useState('')
  const [imageUrl, setImageUrl] = useState('') // 可选：额外图片 URL（可与上传的图片一起发送）
  const [uploadedFiles, setUploadedFiles] = useState<UploadFile[]>([])
  const [qualityInfos, setQualityInfos] = useState<Record<string, QualityInfo>>({}) // fileUrl -> 清晰度
  const [ocrResult, setOcrResult] = useState<string | null>(null) // OCR识别结果（后续通过OCR服务调用生成）
  const [selectedModel, setSelectedModel] = useState<string>('')
  const [availableModels, setAvailableModels] = useState<string[]>([])
  const [result, setResult] = useState<string | null>(null)
  const [tokenInfo, setTokenInfo] = useState<{
    inputTokens?: number
    outputTokens?: number
    totalTokens?: number
  } | null>(null)
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [serviceStatus, setServiceStatus] = useState<{
    available: boolean
    loading: boolean
  }>({ available: false, loading: true })

  // 检查服务状态
  const checkServiceStatus = async () => {
    setServiceStatus((prev) => ({ ...prev, loading: true }))
    try {
      const health = await checkHealth()
      setServiceStatus({ available: health.serviceAvailable, loading: false })
      // 更新可用模型列表
      if (health.availableModels && health.availableModels.length > 0) {
        setAvailableModels(health.availableModels)
        // 如果还没有选择模型，默认选择第一个
        if (!selectedModel && health.availableModels.length > 0) {
          setSelectedModel(health.availableModels[0])
        }
      }
    } catch (err) {
      setServiceStatus({ available: false, loading: false })
    }
  }

  // 组件加载时检查服务状态
  useEffect(() => {
    checkServiceStatus()
  }, [])

  // 调用模型推理
  const handleInfer = async () => {
    if (!prompt.trim()) {
      setError('请输入提示词')
      return
    }

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const urlsFromUpload = uploadedFiles.map((f) => f.url).filter((u): u is string => Boolean(u))
      const extraUrl = imageUrl.trim() || null
      const imageUrls = extraUrl
        ? [...urlsFromUpload, extraUrl]
        : urlsFromUpload

      const request: InferenceRequest = {
        prompt: prompt.trim(),
        imageUrls: imageUrls.length > 0 ? imageUrls : undefined,
        imageUrl: imageUrls.length === 1 ? imageUrls[0] : undefined,
        ocrText: ocrResult && ocrResult.trim() ? ocrResult.trim() : null,
        model: selectedModel || null,
      }

      const response = await infer(request)

      if (response.success) {
        setResult(response.content || '')
        setTokenInfo({
          inputTokens: response.inputTokens,
          outputTokens: response.outputTokens,
          totalTokens: response.totalTokens,
        })
        // 设置OCR识别结果（从后端返回，只有当有内容时才设置）
        if (response.ocrText && response.ocrText.trim()) {
          setOcrResult(response.ocrText.trim())
        } else {
          setOcrResult(null) // 清空OCR结果
        }
      } else {
        setError(response.error || '推理失败')
        setTokenInfo(null)
        setOcrResult(null) // 清空OCR结果
      }
    } catch (err: any) {
      console.error('推理调用失败:', err)
      setError(err.message || '调用失败，请检查服务是否运行')
      setResult(null)
      setTokenInfo(null)
      setOcrResult(null)
    } finally {
      setLoading(false)
    }
  }

  // 文件上传处理（支持多张）
  const handleUpload = async (file: File) => {
    setUploading(true)
    setError(null)

    try {
      const formData = new FormData()
      formData.append('file', file)

      const response = await fetch('/api/llm/upload', {
        method: 'POST',
        body: formData,
      })

      const result = await response.json()

      if (result.success) {
        const fileUrl = result.fileUrl as string
        const newEntry: UploadFile = {
          uid: `${file.name}-${Date.now()}`,
          name: file.name,
          status: 'done',
          url: fileUrl,
        }

        setUploadedFiles((prev) => [...prev, newEntry])

        let quality: QualityInfo | undefined
        if (result.quality) {
          quality = {
            width: result.width,
            height: result.height,
            resolution: result.resolution,
            quality: result.quality,
          }
        } else {
          try {
            const qualityResponse = await getFileQuality(fileUrl)
            if (qualityResponse.success && qualityResponse.quality) {
              quality = {
                width: qualityResponse.width!,
                height: qualityResponse.height!,
                resolution: qualityResponse.resolution!,
                quality: qualityResponse.quality!,
              }
            }
          } catch (err) {
            console.debug('获取清晰度信息失败:', err)
          }
        }
        if (quality) {
          setQualityInfos((prev) => ({ ...prev, [fileUrl]: quality! }))
        }

        message.success('图片上传成功')
      } else {
        setError(result.error || '上传失败')
        message.error(result.error || '上传失败')
      }
    } catch (err: any) {
      const errorMsg = err.message || '上传失败，请检查服务是否运行'
      setError(errorMsg)
      message.error(errorMsg)
    } finally {
      setUploading(false)
    }

    return false // 阻止默认上传行为
  }

  // 删除某张已上传的图片
  const handleRemoveFile = (file: UploadFile) => {
    setUploadedFiles((prev) => prev.filter((f) => f.uid !== file.uid))
    if (file.url) {
      setQualityInfos((prev) => {
        const next = { ...prev }
        delete next[file.url as string]
        return next
      })
    }
  }

  // 清空输入
  const handleClear = () => {
    setPrompt('')
    setImageUrl('')
    setUploadedFiles([])
    setQualityInfos({})
    setOcrResult(null)
    setResult(null)
    setError(null)
    setTokenInfo(null)
  }

  return (
    <div className="local-llm-container">
      {/* 标题和服务状态合并在一行 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, marginBottom: 4 }}>
            本地大模型客户端
          </Title>
          <Text type="secondary">调用本地部署的大模型（Ollama/vLLM）</Text>
        </div>
        <Space>
          <Space>
            {serviceStatus.loading ? (
              <Spin size="small" />
            ) : serviceStatus.available ? (
              <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />
            ) : (
              <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 16 }} />
            )}
            <Text type="secondary" style={{ fontSize: 14 }}>
              服务状态: {serviceStatus.available ? '可用' : '不可用'}
            </Text>
          </Space>
          <Button size="small" onClick={checkServiceStatus} loading={serviceStatus.loading}>
            刷新
          </Button>
          <Tag color={serviceStatus.available ? 'success' : 'error'}>
            {serviceStatus.available ? '正常' : '异常'}
          </Tag>
        </Space>
      </div>

      {/* 错误提示 */}
      {error && (
        <Alert
          message="错误"
          description={error}
          type="error"
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 24 }}
        />
      )}

      <Row gutter={24}>
        {/* 左侧：输入区域 */}
        <Col xs={24} lg={12}>
          <Card title="输入" extra={<Text type="secondary">提示词和上下文</Text>}>
            <Space direction="vertical" style={{ width: '100%' }} size="large">
              <div>
                <Text strong>选择模型</Text>
                <Select
                  style={{ width: '100%' }}
                  placeholder="选择要使用的模型"
                  value={selectedModel || undefined}
                  onChange={setSelectedModel}
                  disabled={loading || availableModels.length === 0}
                  options={availableModels.map(model => ({
                    label: model,
                    value: model,
                  }))}
                />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  选择要使用的本地模型
                </Text>
              </div>

              <div>
                <Text strong>提示词 *</Text>
                <TextArea
                  rows={6}
                  placeholder="请输入提示词，例如：你好，请介绍一下你自己"
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  disabled={loading}
                />
              </div>

              <div>
                <Text strong>图片（可选，可多张）</Text>
                <Space direction="vertical" style={{ width: '100%' }} size="small">
                  <Upload
                    beforeUpload={handleUpload}
                    onRemove={handleRemoveFile}
                    fileList={uploadedFiles}
                    maxCount={10}
                    accept="image/*"
                    disabled={loading || uploading}
                  >
                    <Button
                      icon={<UploadOutlined />}
                      loading={uploading}
                      disabled={loading || uploading}
                    >
                      上传图片（可多选）
                    </Button>
                  </Upload>
                  {uploadedFiles.length > 0 && (
                    <div style={{ padding: '4px 0', fontSize: 12 }}>
                      <Text type="secondary">已上传 {uploadedFiles.length} 张</Text>
                      {uploadedFiles.some((f) => f.url && qualityInfos[f.url as string]) && (
                        <div style={{ marginTop: 4 }}>
                          {uploadedFiles.map(
                            (f) =>
                              f.url &&
                              qualityInfos[f.url] && (
                                <div
                                  key={f.uid}
                                  style={{
                                    padding: '4px 8px',
                                    background: '#f0f7ff',
                                    borderRadius: 4,
                                    marginTop: 4,
                                  }}
                                >
                                  <Text type="secondary" style={{ fontSize: 12 }}>
                                    {f.name}: {qualityInfos[f.url].quality} | {qualityInfos[f.url].resolution}
                                  </Text>
                                </div>
                              )
                          )}
                        </div>
                      )}
                    </div>
                  )}
                  <Input
                    placeholder="或输入额外图片 URL（可选）"
                    value={imageUrl}
                    onChange={(e) => setImageUrl(e.target.value)}
                    disabled={loading}
                    allowClear
                  />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    支持一次上传多张图片或输入图片 URL，用于多模态推理
                  </Text>
                </Space>
              </div>

              <Space>
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  onClick={handleInfer}
                  loading={loading}
                  disabled={!prompt.trim()}
                >
                  发送
                </Button>
                <Button icon={<ClearOutlined />} onClick={handleClear} disabled={loading}>
                  清空
                </Button>
              </Space>
            </Space>
          </Card>
        </Col>

        {/* 右侧：输出区域 */}
        <Col xs={24} lg={12}>
          <Card
            title="输出"
            extra={
              result && (
                <Space>
                  {tokenInfo?.totalTokens && (
                    <Tag color="blue">
                      {tokenInfo.totalTokens} tokens
                    </Tag>
                  )}
                  <Tag color="success">
                    {result.length} 字符
                  </Tag>
                </Space>
              )
            }
          >
            {loading ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}>
                <Spin size="large" />
                <div style={{ marginTop: 16 }}>
                  <Text type="secondary">正在调用本地模型...</Text>
                </div>
              </div>
            ) : result ? (
              <div>
                {/* OCR识别结果（预留位置，后续通过OCR服务调用生成） */}
                {ocrResult && ocrResult.trim() && (
                  <div style={{ marginBottom: 16, padding: 12, background: '#f0f7ff', borderRadius: 4, border: '1px solid #d4edda' }}>
                    <Text strong style={{ fontSize: 14, color: '#1890ff' }}>OCR识别结果：</Text>
                    <div style={{ marginTop: 8 }}>
                      <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0, fontSize: 12, color: '#666' }}>
                        {ocrResult}
                      </pre>
                    </div>
                  </div>
                )}
                
                {/* Token统计信息 */}
                {tokenInfo && (tokenInfo.inputTokens || tokenInfo.outputTokens) && (
                  <div style={{ marginBottom: 12, padding: 8, background: '#f5f5f5', borderRadius: 4 }}>
                    <Space split={<Divider type="vertical" />}>
                      {tokenInfo.inputTokens !== null && tokenInfo.inputTokens !== undefined && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          输入: <Text strong>{tokenInfo.inputTokens}</Text> tokens
                        </Text>
                      )}
                      {tokenInfo.outputTokens !== null && tokenInfo.outputTokens !== undefined && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          输出: <Text strong>{tokenInfo.outputTokens}</Text> tokens
                        </Text>
                      )}
                      {tokenInfo.totalTokens !== null && tokenInfo.totalTokens !== undefined && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          总计: <Text strong>{tokenInfo.totalTokens}</Text> tokens
                        </Text>
                      )}
                    </Space>
                  </div>
                )}
                
                {/* 模型推理结果 */}
                <div className="result-content">
                  <Text strong style={{ fontSize: 14, marginBottom: 8, display: 'block' }}>模型推理结果：</Text>
                  <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                    {result}
                  </pre>
                </div>
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
                <Text type="secondary">结果将显示在这里</Text>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* 使用说明 */}
      <Card size="small" style={{ marginTop: 24 }}>
        <Title level={5}>使用说明</Title>
        <ul style={{ margin: 0, paddingLeft: 20 }}>
          <li>
            <Text>确保本地模型服务已启动（如 Ollama: http://localhost:11434）</Text>
          </li>
          <li>
            <Text>提示词为必填项，图片为可选项</Text>
          </li>
          <li>
            <Text>上传图片后，系统将自动调用OCR服务生成识别结果（待实现）</Text>
          </li>
        </ul>
      </Card>
    </div>
  )
}

export default LocalLLM

