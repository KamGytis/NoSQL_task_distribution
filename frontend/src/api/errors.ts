import axios from 'axios'

interface ApiError {
    message?: string
    errors?: { defaultMessage?: string; field?: string }[]
}

export function getErrorMessage(err: unknown): string {
    if (axios.isAxiosError<ApiError>(err)) {
        if (!err.response) return 'Cannot reach the server. Is the backend running?'
        const data = err.response.data
        if (err.response.status === 400 && data?.errors?.length) {
            return data.errors
                .map((e) => (e.field ? `${e.field}: ` : '') + (e.defaultMessage ?? 'invalid'))
                .join('; ')
        }
        if (data?.message) return data.message
        return `Request failed (${err.response.status})`
    }
    return 'Something went wrong'
}