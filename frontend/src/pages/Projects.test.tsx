import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as projectsApi from '../api/projects'
import { makeAxiosError } from '../test/axiosError'
import { makeProject, pendingInvitation } from '../test/fixtures'
import Projects from './Projects'

vi.mock('../api/projects')

function renderPage() {
    render(
        <MemoryRouter>
            <Projects />
        </MemoryRouter>,
    )
    return userEvent.setup()
}

describe('Projects page', () => {
    beforeEach(() => {
        vi.resetAllMocks()
        vi.mocked(projectsApi.listProjects).mockResolvedValue([makeProject('ADMIN')])
        vi.mocked(projectsApi.listInvitations).mockResolvedValue([])
    })

    it('lists my projects with key, role and member count', async () => {
        renderPage()

        expect(await screen.findByText('Demo project')).toBeInTheDocument()
        expect(screen.getByText('DEMO')).toBeInTheDocument()
        expect(screen.getByText('Admin')).toBeInTheDocument()
        expect(screen.getByText('2 members')).toBeInTheDocument()
        expect(screen.getByText('First project')).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Settings' })).toHaveAttribute('href', '/projects/p1/settings')
    })

    it('explains what to do when there are no projects', async () => {
        vi.mocked(projectsApi.listProjects).mockResolvedValue([])
        renderPage()

        expect(await screen.findByText(/not in any project yet/)).toBeInTheDocument()
    })

    it('shows the error when loading fails', async () => {
        vi.mocked(projectsApi.listProjects).mockRejectedValue(makeAxiosError(500, { message: 'Server is down' }))
        renderPage()

        expect(await screen.findByText('Server is down')).toBeInTheDocument()
    })

    it('creates a project with an upper-cased key and refreshes the list', async () => {
        vi.mocked(projectsApi.createProject).mockResolvedValue(makeProject('ADMIN'))
        const ue = renderPage()
        await screen.findByText('Demo project')

        await ue.click(screen.getByRole('button', { name: 'New project' }))
        await ue.type(screen.getByLabelText('Key'), 'dem1')
        await ue.type(screen.getByLabelText('Name'), '  Demo  ')
        await ue.click(screen.getByRole('button', { name: 'Create project' }))

        await waitFor(() =>
            expect(projectsApi.createProject).toHaveBeenCalledWith({ key: 'DEM1', name: 'Demo', description: null }),
        )
        await waitFor(() => expect(projectsApi.listProjects).toHaveBeenCalledTimes(2))
        expect(screen.queryByLabelText('Key')).not.toBeInTheDocument()
    })

    it('rejects an invalid key without calling the server', async () => {
        const ue = renderPage()
        await screen.findByText('Demo project')

        await ue.click(screen.getByRole('button', { name: 'New project' }))
        await ue.type(screen.getByLabelText('Key'), 'a')
        await ue.type(screen.getByLabelText('Name'), 'Demo')
        await ue.click(screen.getByRole('button', { name: 'Create project' }))

        expect(
            await screen.findByText('Key must be 2-10 capital letters or digits and start with a letter'),
        ).toBeInTheDocument()
        expect(projectsApi.createProject).not.toHaveBeenCalled()
    })

    it('shows the server error when the key is taken and keeps the form open', async () => {
        vi.mocked(projectsApi.createProject).mockRejectedValue(
            makeAxiosError(409, { message: 'Project key already in use' }),
        )
        const ue = renderPage()
        await screen.findByText('Demo project')

        await ue.click(screen.getByRole('button', { name: 'New project' }))
        await ue.type(screen.getByLabelText('Key'), 'DEMO')
        await ue.type(screen.getByLabelText('Name'), 'Again')
        await ue.click(screen.getByRole('button', { name: 'Create project' }))

        expect(await screen.findByText('Project key already in use')).toBeInTheDocument()
        expect(screen.getByLabelText('Key')).toBeInTheDocument()
    })

    it('shows pending invitations and accepting one refreshes everything', async () => {
        vi.mocked(projectsApi.listInvitations).mockResolvedValueOnce([pendingInvitation]).mockResolvedValue([])
        vi.mocked(projectsApi.acceptInvitation).mockResolvedValue(makeProject('VIEWER'))
        const ue = renderPage()

        expect(await screen.findByText(/invited you to join/)).toBeInTheDocument()
        expect(screen.getByText('Anna Admin')).toBeInTheDocument()
        expect(screen.getByText('Shared project')).toBeInTheDocument()

        await ue.click(screen.getByRole('button', { name: 'Accept' }))

        await waitFor(() => expect(projectsApi.acceptInvitation).toHaveBeenCalledWith('i1'))
        await waitFor(() => expect(screen.queryByText(/invited you to join/)).not.toBeInTheDocument())
        expect(projectsApi.listProjects).toHaveBeenCalledTimes(2)
    })

    it('declines an invitation', async () => {
        vi.mocked(projectsApi.listInvitations).mockResolvedValueOnce([pendingInvitation]).mockResolvedValue([])
        vi.mocked(projectsApi.declineInvitation).mockResolvedValue({} as never)
        const ue = renderPage()

        await ue.click(await screen.findByRole('button', { name: 'Decline' }))

        await waitFor(() => expect(projectsApi.declineInvitation).toHaveBeenCalledWith('i1'))
        expect(projectsApi.acceptInvitation).not.toHaveBeenCalled()
    })

    it('shows the error when an invitation cannot be accepted', async () => {
        vi.mocked(projectsApi.listInvitations).mockResolvedValue([pendingInvitation])
        vi.mocked(projectsApi.acceptInvitation).mockRejectedValue(
            makeAxiosError(409, { message: 'Invitation already answered' }),
        )
        const ue = renderPage()

        await ue.click(await screen.findByRole('button', { name: 'Accept' }))

        expect(await screen.findByText('Invitation already answered')).toBeInTheDocument()
    })
})