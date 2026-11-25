export interface User {
    id: string
    firstName: string
    lastName: string
    email: string
    birthYear: number
    createdAt: string
}

export interface LoginPayload {
    email: string
    password: string
}

export interface RegisterPayload extends LoginPayload {
    firstName: string
    lastName: string
    birthYear: number
}
export type ProjectRole = 'ADMIN' | 'MEMBER' | 'VIEWER'

export interface Member {
    userId: string
    firstName: string | null
    lastName: string | null
    email: string | null
    role: ProjectRole
}

export interface Project {
    id: string
    key: string
    name: string
    description: string | null
    leadId: string
    members: Member[]
    myRole: ProjectRole | null
    createdAt: string
}

export interface Invitation {
    id: string
    projectId: string
    projectName: string
    role: ProjectRole
    status: 'PENDING' | 'ACCEPTED' | 'DECLINED'
    invitedByName: string | null
    createdAt: string
}

export interface CreateProjectPayload {
    key: string
    name: string
    description: string | null
}

export interface UpdateProjectPayload {
    name: string
    description: string | null
}