import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export default function Dashboard() {
    const { user, logout } = useAuth()
    const navigate = useNavigate()

    async function onLogout() {
        await logout()
        navigate('/login', { replace: true })
    }

    return (
        <div className="auth-page">
            <div className="card">
                <h1>Welcome, {user?.firstName}!</h1>
                <p className="hint">{user?.email}</p>
                <p>Projects will appear here.</p>
                <button onClick={onLogout}>Log out</button>
            </div>
        </div>
    )
}