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