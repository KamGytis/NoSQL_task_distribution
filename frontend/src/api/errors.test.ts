import { describe, expect, it } from 'vitest'
import { makeAxiosError } from '../test/axiosError'
import { getErrorMessage } from './errors'

describe('getErrorMessage', () => {
    it('explains when the backend cannot be reached', () => {
        expect(getErrorMessage(makeAxiosError(null))).toContain('Cannot reach the server')
    })

    it('uses the message sent by the backend', () => {
        const err = makeAxiosError(409, { message: 'Email already registered' })
        expect(getErrorMessage(err)).toBe('Email already registered')
    })

    it('joins validation errors with their field names', () => {
        const err = makeAxiosError(400, {
            errors: [
                { field: 'email', defaultMessage: 'must be a well-formed email address' },
                { field: 'password', defaultMessage: 'size must be between 8 and 72' },
            ],
        })
        expect(getErrorMessage(err)).toBe(
            'email: must be a well-formed email address; password: size must be between 8 and 72',
        )
    })

    it('falls back to the status code', () => {
        expect(getErrorMessage(makeAxiosError(500, {}))).toBe('Request failed (500)')
    })

    it('handles errors that are not from axios', () => {
        expect(getErrorMessage(new Error('boom'))).toBe('Something went wrong')
    })
})