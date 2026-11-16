import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { jonas } from '../test/fixtures'
import type { AuthContextValue } from './AuthContext'
import ProtectedRoute from './ProtectedRoute'
import { useAuth } from './useAuth'

vi.mock('./useAuth')

function renderAtSecret(state: Pick<AuthContextValue, 'user' | 'loading'>) {
    vi.mocked(useAuth).mockReturnValue({
        ...state,
        login: vi.fn(),
        register: vi.fn(),
        logout: vi.fn(),
    })
    render(
        <MemoryRouter initialEntries={['/secret']}>
            <Routes>
                <Route path="/login" element={<p>login page</p>} />
                <Route element={<ProtectedRoute />}>
                    <Route path="/secret" element={<p>secret page</p>} />
                </Route>
            </Routes>
        </MemoryRouter>,
    )
}

describe('ProtectedRoute', () => {
    it('shows a loading message while the session is being checked', () => {
        renderAtSecret({ user: null, loading: true })
        expect(screen.getByText('Loading...')).toBeInTheDocument()
        expect(screen.queryByText('secret page')).not.toBeInTheDocument()
    })

    it('redirects anonymous visitors to the login page', () => {
        renderAtSecret({ user: null, loading: false })
        expect(screen.getByText('login page')).toBeInTheDocument()
    })

    it('renders the page for a logged-in user', () => {
        renderAtSecret({ user: jonas, loading: false })
        expect(screen.getByText('secret page')).toBeInTheDocument()
    })
})