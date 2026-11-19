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
| GET | /users/me/export | Download data archive | Done |

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

## Saved filters
| Method | Endpoint | Description | Status |
|---|---|---|---|
| POST | /saved-filters | Create a personal filter | Done |
| GET | /saved-filters | List my filters | Done |
| GET | /saved-filters/{id} | Get my filter | Done |
| PUT | /saved-filters/{id} | Replace filter settings | Done |
| DELETE | /saved-filters/{id} | Delete my filter | Done |
| GET | /saved-filters/{id}/issues | Apply filter to current issues | Done |

## Reports
| Method | Endpoint | Description | Status |
|---|---|---|---|
| GET | /projects/{projectId}/reports/events | Event counts by type | Done |
| GET | /projects/{projectId}/reports/activity | Daily event counts by type | Done |

## Search and suggestions
| Method | Endpoint | Description | Status |
|---|---|---|---|
| GET | /projects/{projectId}/search | Search project issues | Done |
| GET | /projects/{projectId}/suggest | Suggest issue keys and titles | Done |

## User data export
GET /api/v1/users/me/export requires authentication.
The authenticated user determines the archive owner.

Successful responses return:

- 200 OK.
- Content-Type: application/zip.
- Content-Disposition: attachment; filename="user-data.zip".
- Cache-Control: no-store.

The archive contains UTF-8 JSON files:

| File | Content |
|---|---|
| profile.json | Current user's public profile fields |
| projects.json | Current project memberships and project metadata |
| issues.json | Reported or assigned issues in accessible projects |
| comments.json | Comments authored by the user |
| worklogs.json | Worklogs recorded by the user |
| saved-filters.json | Filters owned by the user |
| notifications.json | Notifications addressed to the user |
| invitations.json | Invitations sent or received by the user |
| issue-history.json | History entries whose actor is the user |
| manifest.json | Format version, export timestamp and record counts |

Project membership arrays contain only the exporting user's membership.
Authored records can include references to former projects.

The profile uses the existing UserResponse representation.
Collection records use MongoDB Relaxed Extended JSON.
Object IDs and dates may use $oid and $date wrappers.

Empty collections are represented by [].
Passwords, password hashes, session data and internal indexing flags
are excluded.

Unauthenticated requests return 401.
Handled archive-generation or database failures return 503.

The archive is assembled before the HTTP response is sent.
Collections are read separately and are not a cross-collection snapshot.
The compressed ZIP is held in server memory during generation.

## Status codes
201 created, 400 validation error, 401 not logged in, 403 no permission, 404 not found, 409 conflict