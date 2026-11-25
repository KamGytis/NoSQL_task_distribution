import type { Invitation, Project, ProjectRole, User } from '../types'

export const jonas: User = {
    id: '1',
    firstName: 'Jonas',
    lastName: 'Jonaitis',
    email: 'jonas@test.lt',
    birthYear: 2000,
    createdAt: '2026-01-01T00:00:00Z',
}

/** Alice (u1) is the admin and Bob (u2) a member. myRole is the role of whoever is looking. */
export function makeProject(myRole: ProjectRole = 'ADMIN'): Project {
    return {
        id: 'p1',
        key: 'DEMO',
        name: 'Demo project',
        description: 'First project',
        leadId: 'u1',
        members: [
            { userId: 'u1', firstName: 'Alice', lastName: 'Admin', email: 'alice@test.lt', role: 'ADMIN' },
            { userId: 'u2', firstName: 'Bob', lastName: 'Builder', email: 'bob@test.lt', role: 'MEMBER' },
        ],
        myRole,
        createdAt: '2026-01-01T00:00:00Z',
    }
}

export const pendingInvitation: Invitation = {
    id: 'i1',
    projectId: 'p9',
    projectName: 'Shared project',
    role: 'VIEWER',
    status: 'PENDING',
    invitedByName: 'Anna Admin',
    createdAt: '2026-01-02T00:00:00Z',
}