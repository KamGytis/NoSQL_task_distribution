import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getErrorMessage } from '../api/errors'
import { changeMemberRole, deleteProject, getProject, inviteMember, removeMember, updateProject } from '../api/projects'
import { useAuth } from '../auth/useAuth'
import { roleLabel } from '../projects/helpers'
import type { Project, ProjectRole } from '../types'

const ROLES: ProjectRole[] = ['ADMIN', 'MEMBER', 'VIEWER']

function statusOf(err: unknown): number | undefined {
    return (err as { response?: { status?: number } }).response?.status
}

export default function ProjectSettings() {
    const { id } = useParams()
    const { user } = useAuth()
    const navigate = useNavigate()

    const [project, setProject] = useState<Project | null>(null)
    const [loadError, setLoadError] = useState<{ notFound: boolean; message: string } | null>(null)
    const [name, setName] = useState('')
    const [description, setDescription] = useState('')
    const [inviteEmail, setInviteEmail] = useState('')
    const [inviteRole, setInviteRole] = useState<ProjectRole>('MEMBER')
    const [deleteConfirm, setDeleteConfirm] = useState('')
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)

    // All setState calls happen after an await, so nothing is set synchronously inside the effect.
    useEffect(() => {
        if (!id) return
        let cancelled = false
        getProject(id)
            .then((p) => {
                if (cancelled) return
                setProject(p)
                setName(p.name)
                setDescription(p.description ?? '')
            })
            .catch((err) => {
                if (cancelled) return
                setLoadError({ notFound: statusOf(err) === 404, message: getErrorMessage(err) })
            })
        return () => {
            cancelled = true
        }
    }, [id])

    if (loadError?.notFound) {
        return (
            <main>
                <h1>Project not found</h1>
                <Link to="/">Back to projects</Link>
            </main>
        )
    }
    if (loadError) {
        return (
            <main>
                <p role="alert">{loadError.message}</p>
            </main>
        )
    }
    if (!project || !id) {
        return (
            <main>
                <p>Loading…</p>
            </main>
        )
    }

    const myRole = project.members.find((m) => m.userId === user?.id)?.role
    const isAdmin = myRole === 'ADMIN'

    function begin() {
        setError(null)
        setNotice(null)
    }

    async function onSave(e: FormEvent) {
        e.preventDefault()
        begin()
        try {
            const saved = await updateProject(id!, { name: name.trim(), description })
            setProject(saved)
            setNotice('Saved')
        } catch (err) {
            setError(getErrorMessage(err))
        }
    }

    async function onChangeRole(userId: string, role: ProjectRole) {
        begin()
        try {
            setProject(await changeMemberRole(id!, userId, role))
        } catch (err) {
            setError(getErrorMessage(err))
        }
    }

    async function onRemove(userId: string, email: string) {
        if (!window.confirm(`Remove ${email} from this project?`)) return
        begin()
        try {
            await removeMember(id!, userId)
            setProject(await getProject(id!))
        } catch (err) {
            setError(getErrorMessage(err))
        }
    }

    async function onInvite(e: FormEvent) {
        e.preventDefault()
        begin()
        const email = inviteEmail.trim()
        if (!email) return
        try {
            await inviteMember(id!, email, inviteRole)
            setNotice(`Invitation sent to ${email}`)
            setInviteEmail('')
        } catch (err) {
            setError(getErrorMessage(err))
        }
    }

    async function onLeave() {
        if (!user || !window.confirm('Leave this project?')) return
        begin()
        try {
            await removeMember(id!, user.id)
            navigate('/')
        } catch (err) {
            setError(getErrorMessage(err))
        }
    }

    async function onDelete() {
        begin()
        try {
            await deleteProject(id!)
            navigate('/')
        } catch (err) {
            setError(getErrorMessage(err))
        }
    }

    return (
        <main>
            <Link to="/">← Projects</Link>
            <h1>{project.name}</h1>

            {error && <p role="alert">{error}</p>}
            {notice && <p role="status">{notice}</p>}

            <section>
                <h2>Details</h2>
                <form onSubmit={onSave}>
                    <label htmlFor="ps-key">Key</label>
                    <input id="ps-key" value={project.key} disabled readOnly />

                    <label htmlFor="ps-name">Name</label>
                    <input id="ps-name" value={name} onChange={(e) => setName(e.target.value)} disabled={!isAdmin} />

                    <label htmlFor="ps-desc">Description</label>
                    <textarea
                        id="ps-desc"
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        disabled={!isAdmin}
                    />

                    {isAdmin && <button type="submit">Save changes</button>}
                </form>
            </section>

            <section>
                <h2>Members</h2>
                <table>
                    <tbody>
                    {project.members.map((m) => (
                        <tr key={m.userId}>
                            <td>{m.email}</td>
                            <td>
                                {isAdmin ? (
                                    <select
                                        aria-label={`Role of ${m.email}`}
                                        value={m.role}
                                        onChange={(e) => void onChangeRole(m.userId, e.target.value as ProjectRole)}
                                    >
                                        {ROLES.map((r) => (
                                            <option key={r} value={r}>
                                                {roleLabel(r)}
                                            </option>
                                        ))}
                                    </select>
                                ) : (
                                    roleLabel(m.role)
                                )}
                            </td>
                            <td>
                                {isAdmin && m.userId !== user?.id && (
                                    <button type="button" onClick={() => void onRemove(m.userId, m.email ?? m.userId)}>                                        Remove {m.email}
                                    </button>
                                )}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </section>

            {isAdmin && (
                <section>
                    <h2>Invite someone</h2>
                    <form onSubmit={onInvite} noValidate>
                        <label htmlFor="ps-email">Email</label>
                        <input
                            id="ps-email"
                            type="email"
                            value={inviteEmail}
                            onChange={(e) => setInviteEmail(e.target.value)}
                        />
                        <label htmlFor="ps-role">Role</label>
                        <select
                            id="ps-role"
                            value={inviteRole}
                            onChange={(e) => setInviteRole(e.target.value as ProjectRole)}
                        >
                            {ROLES.map((r) => (
                                <option key={r} value={r}>
                                    {roleLabel(r)}
                                </option>
                            ))}
                        </select>
                        <button type="submit">Send invitation</button>
                    </form>
                </section>
            )}

            <section>
                <h2>{isAdmin ? 'Danger zone' : 'Leave'}</h2>
                <button type="button" onClick={() => void onLeave()}>
                    Leave project
                </button>

                {isAdmin && (
                    <>
                        <label htmlFor="ps-delete">Type {project.key} to confirm deletion</label>
                        <input
                            id="ps-delete"
                            value={deleteConfirm}
                            onChange={(e) => setDeleteConfirm(e.target.value)}
                        />
                        <button type="button" disabled={deleteConfirm !== project.key} onClick={() => void onDelete()}>
                            Delete project
                        </button>
                    </>
                )}
            </section>
        </main>
    )
}