# REST API

Base path: `/api/v1`
Authentication: session cookie (stored in Redis)

## Auth and users
| Method | Endpoint | Description | Status |
|---|---|---|---|
| POST | /auth/register | Register | Done |
| POST | /auth/login | Log in | Done |
| POST | /auth/logout | Log out | Done |
| GET | /users/me | Current user | Done |
| GET | /users/me/export | Download data archive | TODO |

## Projects
| Method | Endpoint | Description | Status |
|---|---|---|---|
| POST | /projects | Create project | Done |
| GET | /projects | My projects | TODO |
| POST | /projects/{id}/members | Add member | Done |

## Issues
| Method | Endpoint | Description | Status |
|---|---|---|---|
| POST | /projects/{id}/issues | Create issue | Done |
| GET | /projects/{id}/issues | List issues | Done |
| GET | /issues/{key} | Issue details | Done |
| PUT | /issues/{key} | Update issue | Done |
| POST | /issues/{key}/transition | Change status | Done |
| DELETE | /issues/{key} | Delete issue | Done |

## Sprints and backlog
| Method | Endpoint | Description | Status |
|---|---|---|---|
| POST | /projects/{projectId}/sprints | Create a planned sprint | Done |
| GET | /projects/{projectId}/sprints | List project sprints | Done |
| GET | /projects/{projectId}/backlog | List issues without a sprint | Done |
| GET | /sprints/{sprintId}/issues | List issues assigned to a sprint | Done |
| PUT | /issues/{key}/sprint | Assign or move an issue to a sprint | Done |
| DELETE | /issues/{key}/sprint | Move an issue back to the backlog | Done |
| POST | /sprints/{sprintId}/start | Start a planned sprint | Done |
| POST | /sprints/{sprintId}/complete | Complete the active sprint | Done |

## Status codes
201 created, 400 validation error, 401 not logged in, 403 no permission, 404 not found, 409 conflict