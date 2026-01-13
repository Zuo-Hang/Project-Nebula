import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from 'antd'
import TaskList from './pages/TaskList'
import TaskDetail from './pages/TaskDetail'
import Header from './components/Header'
import './App.css'

const { Content } = Layout

function App() {
  return (
    <BrowserRouter>
      <Layout style={{ minHeight: '100vh' }}>
        <Header />
        <Content style={{ padding: '24px', background: '#f0f2f5' }}>
          <Routes>
            <Route path="/" element={<TaskList />} />
            <Route path="/tasks/:taskId" element={<TaskDetail />} />
          </Routes>
        </Content>
      </Layout>
    </BrowserRouter>
  )
}

export default App

