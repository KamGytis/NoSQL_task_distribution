import { useState } from 'react'
import { getErrorMessage } from '../api/errors'
import { changeMemberRole, removeMember } from '../api/projects'
import { memberName, ROLES } from '../projects/helpers'
import type { Member, Project, ProjectRole } from '../types'
import RoleBadge from './RoleBadge'

interface Props {
    project: Project
    currentUserId: string
    canManage: boolean
    onProject: (project: Project) => void
    onMembersChanged: () => void
}

export default function MembersTable({ project, currentUserId, canManage, onProject, onMembersChanged }: Props) {
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    async function onRole(userId: string, role: ProjectRole) {
        setError('')
        setBusy(true)
        try {
            onProject(await changeMemberRole(project.id, userId, role))
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setBusy(false)
        }
    }

    async function onRemove(member: Member) {
        if (!window.confirm(`Remove ${memberName(member)} from ${project.name}?`)) return
        setError('')
        setBusy(true)
        try {
            await removeMember(project.id, member.userId)
            onMembersChanged()
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="stack">
            {error && <p className="error">{error}</p>}
            <table className="members-table">
                <thead>
                <tr>
                    <th>Member</th>
                    <th>Role</th>
                    {canManage && (
                        <th>
                            <span className="visually-hidden">Actions</span>
                        </th>
                    )}
                </tr>
                </thead>
                <tbody>
                {project.members.map((m) => {
                    const you = m.userId === currentUserId
                    const name = memberName(m)
                    const who = m.email ?? name
                    return (
                        <tr key={m.userId}>
                            <td>
                                <div>
                                    {name}
                                    {you && <span className="muted"> (you)</span>}
                                </div>
                                {m.email && <div className="muted">{m.email}</div>}
                            </td>
                            <td>
                                {canManage ? (
                                    <select
                                        aria-label={`Role of ${who}`}
                                        value={m.role}
                                        disabled={busy}
                                        onChange={(e) => onRole(m.userId, e.target.value as ProjectRole)}
                                    >
                                        {ROLES.map((r) => (
                                            <option key={r.value} value={r.value}>
                                                {r.label}
                                            </option>
                                        ))}
                                    </select>
                                ) : (
                                    <RoleBadge role={m.role} />
                                )}
                            </td>
                            {canManage && (
                                <td className="cell-right">
                                    {!you && (
                                        <button
                                            type="button"
                                            className="btn-secondary btn-small"
                                            aria-label={`Remove ${who}`}
                                            disabled={busy}
                                            onClick={() => onRemove(m)}
                                        >
                                            Remove
                                        </button>
                                    )}
                                </td>
                            )}
                        </tr>
                    )
                })}
                </tbody>
            </table>
        </div>
    )
}