import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../api/errors'
import { useAuth } from '../auth/useAuth'

export default function Login() {
    const { user, login } = useAuth()
    const navigate = useNavigate()
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [submitting, setSubmitting] = useState(false)

    if (user) return <Navigate to="/" replace />

    async function onSubmit(e: FormEvent) {
        e.preventDefault()
        setError('')
        setSubmitting(true)
        try {
            await login({ email, password })
            navigate('/', { replace: true })
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="auth-page">
            <form className="card" onSubmit={onSubmit}>
                <h1>Log in</h1>
                <label>
                    Email
                    <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                           required autoComplete="email" />
                </label>
                <label>
                    Password
                    <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                           required autoComplete="current-password" />
                </label>
                {error && <p className="error">{error}</p>}
                <button type="submit" disabled={submitting}>
                    {submitting ? 'Logging in...' : 'Log in'}
                </button>
                <p className="hint">
                    No account? <Link to="/register">Register</Link>
                </p>
            </form>
        </div>
    )
}