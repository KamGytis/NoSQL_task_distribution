import { api } from './client'
import type { CreateProjectPayload, Invitation, Project, ProjectRole, UpdateProjectPayload } from '../types'

export const listProjects = () => api.get<Project[]>('/projects').then((r) => r.data)

export const getProject = (id: string) => api.get<Project>(`/projects/${id}`).then((r) => r.data)

export const createProject = (payload: CreateProjectPayload) =>
    api.post<Project>('/projects', payload).then((r) => r.data)

export const updateProject = (id: string, payload: UpdateProjectPayload) =>
    api.put<Project>(`/projects/${id}`, payload).then((r) => r.data)

export const deleteProject = (id: string) => api.delete(`/projects/${id}`)

export const changeMemberRole = (id: string, userId: string, role: ProjectRole) =>
    api.put<Project>(`/projects/${id}/members/${userId}`, { role }).then((r) => r.data)

export const removeMember = (id: string, userId: string) => api.delete(`/projects/${id}/members/${userId}`)

export const inviteMember = (id: string, email: string, role: ProjectRole) =>
    api.post<Invitation>(`/projects/${id}/invitations`, { email, role }).then((r) => r.data)

export const listInvitations = () => api.get<Invitation[]>('/invitations').then((r) => r.data)

export const acceptInvitation = (id: string) =>
    api.post<Project>(`/invitations/${id}/accept`).then((r) => r.data)

export const declineInvitation = (id: string) => api.post(`/invitations/${id}/decline`)