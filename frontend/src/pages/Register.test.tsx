import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuth } from '../auth/useAuth'
import { makeAxiosError } from '../test/axiosError'
import Register from './Register'

vi.mock('../auth/useAuth')

type UE = ReturnType<typeof userEvent.setup>

function setup() {
    const register = vi.fn().mockResolvedValue(undefined)
    vi.mocked(useAuth).mockReturnValue({
        user: null,
        loading: false,
        login: vi.fn(),
        register,
        logout: vi.fn(),
    })
    render(
        <MemoryRouter initialEntries={['/register']}>
            <Routes>
                <Route path="/register" element={<Register />} />
                <Route path="/login" element={<p>login page</p>} />
                <Route path="/" element={<p>home page</p>} />
            </Routes>
        </MemoryRouter>,
    )
    return { register, ue: userEvent.setup() }
}

async function fillForm(ue: UE, password = 'password123', confirm = password) {
    await ue.type(screen.getByLabelText('First name'), 'Jonas')
    await ue.type(screen.getByLabelText('Last name'), 'Jonaitis')
    await ue.type(screen.getByLabelText('Birth year'), '2000')
    await ue.type(screen.getByLabelText('Email'), 'jonas@test.lt')
    await ue.type(screen.getByLabelText('Password'), password)
    await ue.type(screen.getByLabelText('Confirm password'), confirm)
}

describe('Register page', () => {
    beforeEach(() => {
        vi.resetAllMocks()
    })

    it('registers with the entered details and goes to the home page', async () => {
        const { register, ue } = setup()
        await fillForm(ue)
        await ue.click(screen.getByRole('button', { name: 'Register' }))

        expect(register).toHaveBeenCalledWith({
            firstName: 'Jonas',
            lastName: 'Jonaitis',
            birthYear: 2000,
            email: 'jonas@test.lt',
            password: 'password123',
        })
        expect(await screen.findByText('home page')).toBeInTheDocument()
    })

    it('rejects passwords shorter than 8 characters without calling the server', async () => {
        const { register, ue } = setup()
        await fillForm(ue, 'short1')
        await ue.click(screen.getByRole('button', { name: 'Register' }))

        expect(await screen.findByText('Password must be at least 8 characters')).toBeInTheDocument()
        expect(register).not.toHaveBeenCalled()
    })

    it('rejects mismatching passwords without calling the server', async () => {
        const { register, ue } = setup()
        await fillForm(ue, 'password123', 'password124')
        await ue.click(screen.getByRole('button', { name: 'Register' }))

        expect(await screen.findByText('Passwords do not match')).toBeInTheDocument()
        expect(register).not.toHaveBeenCalled()
    })

    it('shows the server error when the email is taken', async () => {
        const { register, ue } = setup()
        register.mockRejectedValue(makeAxiosError(409, { message: 'Email already registered' }))
        await fillForm(ue)
        await ue.click(screen.getByRole('button', { name: 'Register' }))

        expect(await screen.findByText('Email already registered')).toBeInTheDocument()
        expect(screen.queryByText('home page')).not.toBeInTheDocument()
    })
})