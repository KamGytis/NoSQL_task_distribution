import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthContextValue } from '../auth/AuthContext'
import { useAuth } from '../auth/useAuth'
import { makeAxiosError } from '../test/axiosError'
import { jonas } from '../test/fixtures'
import Login from './Login'

vi.mock('../auth/useAuth')

function setup(overrides: Partial<AuthContextValue> = {}) {
    const login = vi.fn().mockResolvedValue(undefined)
    vi.mocked(useAuth).mockReturnValue({
        user: null,
        loading: false,
        login,
        register: vi.fn(),
        logout: vi.fn(),
        ...overrides,
    })
    render(
        <MemoryRouter initialEntries={['/login']}>
            <Routes>
                <Route path="/login" element={<Login />} />
                <Route path="/register" element={<p>register page</p>} />
                <Route path="/" element={<p>home page</p>} />
            </Routes>
        </MemoryRouter>,
    )
    return { login, ue: userEvent.setup() }
}

describe('Login page', () => {
    beforeEach(() => {
        vi.resetAllMocks()
    })

    it('submits the credentials and goes to the home page', async () => {
        const { login, ue } = setup()
        await ue.type(screen.getByLabelText('Email'), 'jonas@test.lt')
        await ue.type(screen.getByLabelText('Password'), 'password123')
        await ue.click(screen.getByRole('button', { name: 'Log in' }))

        expect(login).toHaveBeenCalledWith({ email: 'jonas@test.lt', password: 'password123' })
        expect(await screen.findByText('home page')).toBeInTheDocument()
    })

    it('shows the error returned by the server and stays on the page', async () => {
        const { login, ue } = setup()
        login.mockRejectedValue(makeAxiosError(401, { message: 'Invalid email or password' }))
        await ue.type(screen.getByLabelText('Email'), 'jonas@test.lt')
        await ue.type(screen.getByLabelText('Password'), 'wrong-password')
        await ue.click(screen.getByRole('button', { name: 'Log in' }))

        expect(await screen.findByText('Invalid email or password')).toBeInTheDocument()
        expect(screen.queryByText('home page')).not.toBeInTheDocument()
    })

    it('redirects away when the user is already logged in', () => {
        setup({ user: jonas })
        expect(screen.getByText('home page')).toBeInTheDocument()
    })

    it('links to the register page', () => {
        setup()
        expect(screen.getByRole('link', { name: 'Register' })).toHaveAttribute('href', '/register')
    })
})