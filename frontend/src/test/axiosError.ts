import { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'

// Builds an AxiosError like the ones the real client throws. Pass null for "server unreachable".
export function makeAxiosError(status: number | null, data?: unknown) {
    const response = status === null ? undefined : ({ status, data } as AxiosResponse)
    return new AxiosError('request failed', 'ERR_TEST', {} as InternalAxiosRequestConfig, null, response)
}