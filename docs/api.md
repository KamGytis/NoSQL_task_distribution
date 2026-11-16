# REST API

Base path: `/api/v1`
Authentication: session cookie (stored in Redis)

## Auth and users
| Method | Endpoint | Description | Status |
|---|---|---|---|
| POST | /auth/register | Register | Done |
| POST | /auth/login | Log in | Done |
| POST | /auth/logout | Log out | Done |
| GET | /users/me | Current user | TODO |
| GET | /users/me/export | Download data archive | TODO |

## Projects
| Method | Endpoint | Description | Status |
|---|---|---|---|
| POST | /projects | Create project | TODO |
| GET | /projects | My projects | TODO |
| POST | /projects/{id}/members | Add member | TODO |

## Issues
| Method | Endpoint | Description | Status |
|---|---|---|---|
| POST | /projects/{id}/issues | Create issue | TODO |
| GET | /projects/{id}/issues | List issues | TODO |
| GET | /issues/{key} | Issue details | TODO |
| POST | /issues/{key}/transition | Change status | TODO |

## Status codes
201 created, 400 validation error, 401 not logged in, 403 no permission, 404 not found, 409 conflict