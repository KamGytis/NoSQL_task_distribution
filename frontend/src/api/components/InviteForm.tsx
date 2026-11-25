import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { getErrorMessage } from '../api/errors'
import { inviteMember } from '../api/projects'
import { ROLES } from '../projects/helpers'
import type { ProjectRole } from '../types'

export default function InviteForm({ projectId }: { projectId: string }) {
    const id = useId()
    const [email, setEmail] = useState('')
    const [role, setRole] = useState<ProjectRole>('MEMBER')
    const [error, setError] = useState('')
    const [message, setMessage] = useState('')
    const [sending, setSending] = useState(false)

    async function onSubmit(e: FormEvent) {
        e.preventDefault()
        setError('')
        setMessage('')
        setSending(true)
        const target = email.trim()
        try {
            await inviteMember(projectId, target, role)
            setMessage(`Invitation sent to ${target}`)
            setEmail('')
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setSending(false)
        }
    }

    return (
        <form className="stack invite-form" onSubmit={onSubmit}>
            <h3>Invite someone</h3>
            <div className="form-row">
                <div className="field grow">
                    <label htmlFor={`${id}-email`}>Email</label>
                    <input
                        id={`${id}-email`}
                        type="email"
                        value={email}
                        required
                        autoComplete="off"
                        onChange={(e) => setEmail(e.target.value)}
                    />
                </div>
                <div className="field">
                    <label htmlFor={`${id}-role`}>Role</label>
                    <select id={`${id}-role`} value={role} onChange={(e) => setRole(e.target.value as ProjectRole)}>
                        {ROLES.map((r) => (
                            <option key={r.value} value={r.value}>
                                {r.label}
                            </option>
                        ))}
                    </select>
                </div>
                <button type="submit" disabled={sending || !email.trim()}>
                    Send invitation
                </button>
            </div>
            <p className="hint">{ROLES.find((r) => r.value === role)?.description} The person needs an account already.</p>
            {message && <p className="success">{message}</p>}
            {error && <p className="error">{error}</p>}
        </form>
    )
}