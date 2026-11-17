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

## Status codes
201 created, 400 validation error, 401 not logged in, 403 no permission, 404 not found, 409 conflict