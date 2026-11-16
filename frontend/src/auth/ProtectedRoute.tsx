import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './useAuth'

export default function ProtectedRoute() {
    const { user, loading } = useAuth()
    if (loading) return <p className="center-text">Loading...</p>
    return user ? <Outlet /> : <Navigate to="/login" replace />
}