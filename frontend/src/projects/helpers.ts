import type { Member, ProjectRole } from '../types'

export const ROLES: { value: ProjectRole; label: string; description: string }[] = [
    { value: 'ADMIN', label: 'Admin', description: 'Manages the project, its members and settings.' },
    { value: 'MEMBER', label: 'Member', description: 'Creates and edits issues, comments and logs time.' },
    { value: 'VIEWER', label: 'Viewer', description: 'Read only.' },
]

export const roleLabel = (role: ProjectRole) => ROLES.find((r) => r.value === role)?.label ?? role

export function memberName(member: Member): string {
    const full = [member.firstName, member.lastName].filter(Boolean).join(' ')
    return full || member.email || 'Unknown user'
}