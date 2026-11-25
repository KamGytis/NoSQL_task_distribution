import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { getErrorMessage } from '../api/errors'
import { updateProject } from '../api/projects'
import type { Project } from '../types'

interface Props {
    project: Project
    canEdit: boolean
    onSaved: (project: Project) => void
}

export default function ProjectDetailsForm({ project, canEdit, onSaved }: Props) {
    const id = useId()
    const [name, setName] = useState(project.name)
    const [description, setDescription] = useState(project.description ?? '')
    const [error, setError] = useState('')
    const [saved, setSaved] = useState(false)
    const [saving, setSaving] = useState(false)

    async function onSubmit(e: FormEvent) {
        e.preventDefault()
        setError('')
        setSaved(false)
        setSaving(true)
        try {
            const updated = await updateProject(project.id, {
                name: name.trim(),
                description: description.trim() || null,
            })
            onSaved(updated)
            setSaved(true)
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setSaving(false)
        }
    }

    return (
        <form className="stack" onSubmit={onSubmit}>
            <div className="field">
                <label htmlFor={`${id}-key`}>Key</label>
                <input id={`${id}-key`} value={project.key} disabled readOnly />
                <span className="hint">The key prefixes issue numbers and cannot be changed.</span>
            </div>
            <div className="field">
                <label htmlFor={`${id}-name`}>Name</label>
                <input
                    id={`${id}-name`}
                    value={name}
                    maxLength={100}
                    required
                    disabled={!canEdit}
                    onChange={(e) => {
                        setName(e.target.value)
                        setSaved(false)
                    }}
                />
            </div>
            <div className="field">
                <label htmlFor={`${id}-description`}>Description</label>
                <textarea
                    id={`${id}-description`}
                    rows={3}
                    value={description}
                    maxLength={1000}
                    disabled={!canEdit}
                    onChange={(e) => {
                        setDescription(e.target.value)
                        setSaved(false)
                    }}
                />
            </div>
            {error && <p className="error">{error}</p>}
            {saved && <p className="success">Saved</p>}
            {canEdit ? (
                <div className="actions">
                    <button type="submit" disabled={saving || !name.trim()}>
                        Save changes
                    </button>
                </div>
            ) : (
                <p className="hint">Only project admins can edit these details.</p>
            )}
        </form>
    )
}