import { expect } from '@playwright/test'
import type { Page } from '@playwright/test'

export const PASSWORD = 'password123'

export const uniqueEmail = () => `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@test.lt`

/** A valid project key: a capital letter followed by capital letters or digits. */
export const uniqueKey = () => 'E' + Math.random().toString(36).slice(2, 8).toUpperCase()

export const projectsHeading = (page: Page) => page.getByRole('heading', { name: 'Projects', exact: true })

export async function register(
    page: Page,
    email: string,
    password = PASSWORD,
    confirm = password,
    firstName = 'Jonas',
    lastName = 'Jonaitis',
) {
    await page.goto('/register')
    await page.getByLabel('First name').fill(firstName)
    await page.getByLabel('Last name').fill(lastName)
    await page.getByLabel('Birth year').fill('2000')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password', { exact: true }).fill(password)
    await page.getByLabel('Confirm password').fill(confirm)
    await page.getByRole('button', { name: 'Register' }).click()
}

export async function login(page: Page, email: string, password: string) {
    await page.goto('/login')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password', { exact: true }).fill(password)
    await page.getByRole('button', { name: 'Log in' }).click()
}

export async function logout(page: Page) {
    await page.getByRole('button', { name: 'Log out' }).click()
    await expect(page).toHaveURL(/\/login$/)
}