import { api } from './client'
import type { LoginPayload, RegisterPayload, User } from '../types'

export const registerRequest = (payload: RegisterPayload) =>
    api.post<User>('/auth/register', payload).then((r) => r.data)

export const loginRequest = (payload: LoginPayload) =>
    api.post<User>('/auth/login', payload).then((r) => r.data)

export const logoutRequest = () => api.post('/auth/logout')

export const fetchMe = () => api.get<User>('/users/me').then((r) => r.data)