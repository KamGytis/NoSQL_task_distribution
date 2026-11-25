import { useState } from 'react'
import { getErrorMessage } from '../api/errors'
import { deleteProject, removeMember } from '../api/projects'
import type { Project } from '../types'

interface Props {
    project: Project
    currentUserId: string
    isAdmin: boolean
    onGone: () => void
}

export default function DangerZone({ project, currentUserId, isAdmin, onGone }: Props) {
    const [confirmKey, setConfirmKey] = useState('')
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)

    async function run(action: () => Promise<unknown>) {
        setError('')
        setBusy(true)
        try {
            await action()
            onGone()
        } catch (err) {
            setError(getErrorMessage(err))
            setBusy(false)
        }
    }

    async function onLeave() {
        if (!window.confirm(`Leave ${project.name}? You will need a new invitation to come back.`)) return
        await run(() => removeMember(project.id, currentUserId))
    }

    return (
        <div className="stack">
            <div className="danger-row">
                <div>
                    <strong>Leave this project</strong>
                    <p className="hint">The last admin cannot leave. Promote someone else first.</p>
                </div>
                <button type="button" className="btn-secondary" disabled={busy} onClick={onLeave}>
                    Leave project
                </button>
            </div>
            {isAdmin && (
                <div className="danger-row">
                    <div>
                        <strong>Delete this project</strong>
                        <p className="hint">
                            Type <strong>{project.key}</strong> to confirm. This cannot be undone.
                        </p>
                        <input
                            aria-label={`Type ${project.key} to confirm deletion`}
                            value={confirmKey}
                            autoComplete="off"
                            onChange={(e) => setConfirmKey(e.target.value)}
                        />
                    </div>
                    <button
                        type="button"
                        className="btn-danger"
                        disabled={busy || confirmKey !== project.key}
                        onClick={() => run(() => deleteProject(project.id))}
                    >
                        Delete project
                    </button>
                </div>
            )}
            {error && <p className="error">{error}</p>}
        </div>
    )
}