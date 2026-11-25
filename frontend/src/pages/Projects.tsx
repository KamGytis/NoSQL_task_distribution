import { useCallback, useEffect, useId, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../api/errors'
import {
    acceptInvitation,
    createProject,
    declineInvitation,
    listInvitations,
    listProjects,
} from '../api/projects'
import RoleBadge from '../components/RoleBadge'
import { roleLabel } from '../projects/helpers'
import type { Invitation, Project } from '../types'

const KEY_PATTERN = /^[A-Z][A-Z0-9]{1,9}$/

export default function Projects() {
    const id = useId()
    const [projects, setProjects] = useState<Project[]>([])
    const [invitations, setInvitations] = useState<Invitation[]>([])
    const [loading, setLoading] = useState(true)
    const [loadError, setLoadError] = useState('')
    const [invitationError, setInvitationError] = useState('')
    const [busyInvitation, setBusyInvitation] = useState<string | null>(null)

    const [showForm, setShowForm] = useState(false)
    const [key, setKey] = useState('')
    const [name, setName] = useState('')
    const [description, setDescription] = useState('')
    const [formError, setFormError] = useState('')
    const [creating, setCreating] = useState(false)

    const load = useCallback(async () => {
        try {
            const [loadedProjects, loadedInvitations] = await Promise.all([listProjects(), listInvitations()])
            setProjects(loadedProjects)
            setInvitations(loadedInvitations)
            setLoadError('')
        } catch (err) {
            setLoadError(getErrorMessage(err))
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        void load()
    }, [load])

    async function onCreate(e: FormEvent) {
        e.preventDefault()
        setFormError('')
        if (!KEY_PATTERN.test(key)) {
            setFormError('Key must be 2-10 capital letters or digits and start with a letter')
            return
        }
        setCreating(true)
        try {
            await createProject({ key, name: name.trim(), description: description.trim() || null })
            setKey('')
            setName('')
            setDescription('')
            setShowForm(false)
            await load()
        } catch (err) {
            setFormError(getErrorMessage(err))
        } finally {
            setCreating(false)
        }
    }

    async function answer(invitation: Invitation, accept: boolean) {
        setInvitationError('')
        setBusyInvitation(invitation.id)
        try {
            if (accept) {
                await acceptInvitation(invitation.id)
            } else {
                await declineInvitation(invitation.id)
            }
            await load()
        } catch (err) {
            setInvitationError(getErrorMessage(err))
        } finally {
            setBusyInvitation(null)
        }
    }

    return (
        <div className="stack-lg">
            <div className="page-header">
                <h1>Projects</h1>
                <button type="button" onClick={() => setShowForm((open) => !open)}>
                    {showForm ? 'Cancel' : 'New project'}
                </button>
            </div>

            {showForm && (
                <form className="panel stack" onSubmit={onCreate}>
                    <h2>Create a project</h2>
                    <div className="form-row">
                        <div className="field short">
                            <label htmlFor={`${id}-key`}>Key</label>
                            <input
                                id={`${id}-key`}
                                value={key}
                                maxLength={10}
                                required
                                autoComplete="off"
                                onChange={(e) => setKey(e.target.value.toUpperCase())}
                            />
                        </div>
                        <div className="field grow">
                            <label htmlFor={`${id}-name`}>Name</label>
                            <input
                                id={`${id}-name`}
                                value={name}
                                maxLength={100}
                                required
                                onChange={(e) => setName(e.target.value)}
                            />
                        </div>
                    </div>
                    <div className="field">
                        <label htmlFor={`${id}-description`}>Description</label>
                        <textarea
                            id={`${id}-description`}
                            rows={3}
                            value={description}
                            maxLength={1000}
                            onChange={(e) => setDescription(e.target.value)}
                        />
                    </div>
                    <p className="hint">
                        The key (2-10 capital letters or digits, starting with a letter) prefixes issue numbers, for example
                        DEMO-1, and cannot be changed later.
                    </p>
                    {formError && <p className="error">{formError}</p>}
                    <div className="actions">
                        <button type="submit" disabled={creating}>
                            Create project
                        </button>
                    </div>
                </form>
            )}

            {invitations.length > 0 && (
                <section className="panel">
                    <h2>Invitations</h2>
                    <ul className="plain-list">
                        {invitations.map((inv) => (
                            <li key={inv.id} className="invitation">
                                <p>
                                    <strong>{inv.invitedByName ?? 'Someone'}</strong> invited you to join{' '}
                                    <strong>{inv.projectName}</strong> as {roleLabel(inv.role)}
                                </p>
                                <div className="actions">
                                    <button
                                        type="button"
                                        className="btn-small"
                                        disabled={busyInvitation === inv.id}
                                        onClick={() => answer(inv, true)}
                                    >
                                        Accept
                                    </button>
                                    <button
                                        type="button"
                                        className="btn-secondary btn-small"
                                        disabled={busyInvitation === inv.id}
                                        onClick={() => answer(inv, false)}
                                    >
                                        Decline
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                    {invitationError && <p className="error">{invitationError}</p>}
                </section>
            )}

            {loading && <p className="muted">Loading...</p>}
            {loadError && <p className="error">{loadError}</p>}
            {!loading && !loadError && projects.length === 0 && (
                <p className="muted">You are not in any project yet. Create one, or ask an admin to invite you.</p>
            )}

            <ul className="project-grid">
                {projects.map((p) => (
                    <li key={p.id} className="project-card">
                        <div className="project-card-top">
                            <span className="project-key">{p.key}</span>
                            <RoleBadge role={p.myRole} />
                        </div>
                        <h3>{p.name}</h3>
                        {p.description && <p className="muted">{p.description}</p>}
                        <div className="project-card-bottom">
              <span className="muted">
                {p.members.length} {p.members.length === 1 ? 'member' : 'members'}
              </span>
                            <Link to={`/projects/${p.id}/settings`}>Settings</Link>
                        </div>
                    </li>
                ))}
            </ul>
        </div>
    )
}