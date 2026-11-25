import { expect, test } from '@playwright/test'
import { login, logout, PASSWORD, projectsHeading, register, uniqueEmail } from './helpers'

test('redirects anonymous visitors to the login page', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible()
})

test('registers, lands on the projects page and stays logged in after a reload', async ({ page }) => {
    await register(page, uniqueEmail())

    await expect(projectsHeading(page)).toBeVisible()
    await expect(page.getByText('Jonas Jonaitis')).toBeVisible()

    await page.reload()
    await expect(projectsHeading(page)).toBeVisible()
})

test('logging out protects the app again', async ({ page }) => {
    await register(page, uniqueEmail())
    await expect(projectsHeading(page)).toBeVisible()

    await logout(page)

    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
})

test('logs in with an existing account', async ({ page }) => {
    const email = uniqueEmail()
    await register(page, email)
    await expect(projectsHeading(page)).toBeVisible()
    await logout(page)

    await login(page, email, PASSWORD)

    await expect(projectsHeading(page)).toBeVisible()
})

test('rejects a wrong password', async ({ page }) => {
    const email = uniqueEmail()
    await register(page, email)
    await expect(projectsHeading(page)).toBeVisible()
    await logout(page)

    await login(page, email, 'wrong-password')

    await expect(page.getByText('Invalid email or password')).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
})

test('rejects registering an email that is already taken', async ({ page }) => {
    const email = uniqueEmail()
    await register(page, email)
    await expect(projectsHeading(page)).toBeVisible()
    await logout(page)

    await register(page, email)

    await expect(page.getByText('Email already registered')).toBeVisible()
    await expect(page).toHaveURL(/\/register$/)
})

test('validates the password on the client', async ({ page }) => {
    await register(page, uniqueEmail(), 'short1')
    await expect(page.getByText('Password must be at least 8 characters')).toBeVisible()

    await page.getByLabel('Password', { exact: true }).fill('password123')
    await page.getByLabel('Confirm password').fill('password124')
    await page.getByRole('button', { name: 'Register' }).click()
    await expect(page.getByText('Passwords do not match')).toBeVisible()
})