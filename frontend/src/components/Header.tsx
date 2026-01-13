import { Layout, Typography } from 'antd'
import { Link } from 'react-router-dom'

const { Header: AntHeader } = Layout
const { Title } = Typography

const Header = () => {
  return (
    <AntHeader style={{ background: '#001529', padding: '0 24px', display: 'flex', alignItems: 'center' }}>
      <Link to="/" style={{ color: '#fff', textDecoration: 'none' }}>
        <Title level={4} style={{ color: '#fff', margin: 0 }}>
          AI Agent Orchestrator
        </Title>
      </Link>
    </AntHeader>
  )
}

export default Header

