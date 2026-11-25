import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export default function AppLayout() {
    const { user, logout } = useAuth()
    const navigate = useNavigate()

    async function onLogout() {
        await logout()
        navigate('/login', { replace: true })
    }

    return (
        <div>
            <header className="app-header">
                <Link to="/" className="brand">
                    Tracker
                </Link>
                <div className="header-right">
          <span>
            {user?.firstName} {user?.lastName}
          </span>
                    <button type="button" className="btn-secondary btn-small" onClick={onLogout}>
                        Log out
                    </button>
                </div>
            </header>
            <main className="container">
                <Outlet />
            </main>
        </div>
    )
}