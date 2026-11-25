import { roleLabel } from '../projects/helpers'
import type { ProjectRole } from '../types'

export default function RoleBadge({ role }: { role: ProjectRole | null }) {
    if (!role) return null
    return <span className={`badge badge-${role.toLowerCase()}`}>{roleLabel(role)}</span>
}