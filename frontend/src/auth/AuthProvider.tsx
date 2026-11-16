import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { fetchMe, loginRequest, logoutRequest, registerRequest } from '../api/auth'
import type { LoginPayload, RegisterPayload, User } from '../types'
import { AuthContext } from './AuthContext'
import type { AuthContextValue } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null)
    const [loading, setLoading] = useState(true)

    // checks if the cookie is valid or not
    useEffect(() => {
        let cancelled = false
        fetchMe()
            .then((u) => !cancelled && setUser(u))
            .catch(() => !cancelled && setUser(null))
            .finally(() => !cancelled && setLoading(false))
        return () => {
            cancelled = true
        }
    }, [])

    const login = useCallback(async (payload: LoginPayload) => {
        setUser(await loginRequest(payload))
    }, [])

    const register = useCallback(async (payload: RegisterPayload) => {
        await registerRequest(payload)
        setUser(await loginRequest({ email: payload.email, password: payload.password }))
    }, [])

    const logout = useCallback(async () => {
        try {
            await logoutRequest()
        } finally {
            setUser(null)
        }
    }, [])

    const value = useMemo<AuthContextValue>(
        () => ({ user, loading, login, register, logout }),
        [user, loading, login, register, logout],
    )

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}