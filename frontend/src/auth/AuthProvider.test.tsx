import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { jonas } from '../test/fixtures'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

vi.mock('../api/auth')

function Probe() {
    const { user, loading, login, register, logout } = useAuth()
    return (
        <div>
            <p data-testid="state">
                {loading ? 'loading' : user ? `user:${user.email}` : 'anonymous'}
            </p>
            <button onClick={() => login({ email: 'jonas@test.lt', password: 'password123' })}>login</button>
            <button
                onClick={() =>
                    register({
                        firstName: 'Jonas',
                        lastName: 'Jonaitis',
                        birthYear: 2000,
                        email: 'jonas@test.lt',
                        password: 'password123',
                    })
                }
            >
                register
            </button>
            <button onClick={() => logout().catch(() => {})}>logout</button>
        </div>
    )
}

function renderProvider() {
    render(
        <AuthProvider>
            <Probe />
        </AuthProvider>,
    )
}

const expectState = (text: string) =>
    waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent(text))

describe('AuthProvider', () => {
    beforeEach(() => {
        vi.resetAllMocks()
    })

    it('restores an existing session on load', async () => {
        vi.mocked(authApi.fetchMe).mockResolvedValue(jonas)
        renderProvider()
        expect(screen.getByTestId('state')).toHaveTextContent('loading')
        await expectState('user:jonas@test.lt')
    })

    it('is anonymous when there is no valid session', async () => {
        vi.mocked(authApi.fetchMe).mockRejectedValue(new Error('401'))
        renderProvider()
        await expectState('anonymous')
    })

    it('login stores the returned user', async () => {
        vi.mocked(authApi.fetchMe).mockRejectedValue(new Error('401'))
        vi.mocked(authApi.loginRequest).mockResolvedValue(jonas)
        renderProvider()
        await expectState('anonymous')

        await userEvent.setup().click(screen.getByRole('button', { name: 'login' }))

        await expectState('user:jonas@test.lt')
        expect(authApi.loginRequest).toHaveBeenCalledWith({ email: 'jonas@test.lt', password: 'password123' })
    })

    it('register creates the account and then logs in', async () => {
        vi.mocked(authApi.fetchMe).mockRejectedValue(new Error('401'))
        vi.mocked(authApi.registerRequest).mockResolvedValue(jonas)
        vi.mocked(authApi.loginRequest).mockResolvedValue(jonas)
        renderProvider()
        await expectState('anonymous')

        await userEvent.setup().click(screen.getByRole('button', { name: 'register' }))

        await expectState('user:jonas@test.lt')
        expect(authApi.registerRequest).toHaveBeenCalledOnce()
        expect(authApi.loginRequest).toHaveBeenCalledWith({ email: 'jonas@test.lt', password: 'password123' })
    })

    it('logout clears the user', async () => {
        vi.mocked(authApi.fetchMe).mockResolvedValue(jonas)
        vi.mocked(authApi.logoutRequest).mockResolvedValue({} as never)
        renderProvider()
        await expectState('user:jonas@test.lt')

        await userEvent.setup().click(screen.getByRole('button', { name: 'logout' }))

        await expectState('anonymous')
    })

    it('logout clears the user even when the request fails', async () => {
        vi.mocked(authApi.fetchMe).mockResolvedValue(jonas)
        vi.mocked(authApi.logoutRequest).mockRejectedValue(new Error('network'))
        renderProvider()
        await expectState('user:jonas@test.lt')

        await userEvent.setup().click(screen.getByRole('button', { name: 'logout' }))

        await expectState('anonymous')
    })
})