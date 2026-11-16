import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../api/errors'
import { useAuth } from '../auth/useAuth'

export default function Register() {
    const { user, register } = useAuth()
    const navigate = useNavigate()
    const [form, setForm] = useState({
        firstName: '',
        lastName: '',
        birthYear: '',
        email: '',
        password: '',
        confirm: '',
    })
    const [error, setError] = useState('')
    const [submitting, setSubmitting] = useState(false)

    if (user) return <Navigate to="/" replace />

    const set = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
        setForm((f) => ({ ...f, [field]: e.target.value }))

    async function onSubmit(e: FormEvent) {
        e.preventDefault()
        setError('')
        if (form.password.length < 8) {
            setError('Password must be at least 8 characters')
            return
        }
        if (form.password !== form.confirm) {
            setError('Passwords do not match')
            return
        }
        setSubmitting(true)
        try {
            await register({
                firstName: form.firstName,
                lastName: form.lastName,
                birthYear: Number(form.birthYear),
                email: form.email,
                password: form.password,
            })
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
                <h1>Create account</h1>
                <div className="row">
                    <label>
                        First name
                        <input value={form.firstName} onChange={set('firstName')} required />
                    </label>
                    <label>
                        Last name
                        <input value={form.lastName} onChange={set('lastName')} required />
                    </label>
                </div>
                <label>
                    Birth year
                    <input type="number" min={1900} max={new Date().getFullYear()}
                           value={form.birthYear} onChange={set('birthYear')} required />
                </label>
                <label>
                    Email
                    <input type="email" value={form.email} onChange={set('email')}
                           required autoComplete="email" />
                </label>
                <label>
                    Password
                    <input type="password" value={form.password} onChange={set('password')}
                           required autoComplete="new-password" />
                </label>
                <label>
                    Confirm password
                    <input type="password" value={form.confirm} onChange={set('confirm')}
                           required autoComplete="new-password" />
                </label>
                {error && <p className="error">{error}</p>}
                <button type="submit" disabled={submitting}>
                    {submitting ? 'Creating...' : 'Register'}
                </button>
                <p className="hint">
                    Already registered? <Link to="/login">Log in</Link>
                </p>
            </form>
        </div>
    )
}