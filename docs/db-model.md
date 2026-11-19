# Database Model (MongoDB)

## Collections

| Collection | Purpose | Status |
|---|---|---|
| users | Registered users | Done |
| projects | Projects, members with roles, workflow | Done |
| issues | Epics, stories, tasks, bugs, subtasks | TODO |
| sprints | Sprint planning | Done |
| boards | Board columns and WIP limits | TODO |
| comments | Issue comments with @mentions | TODO |
| worklogs | Time tracking | TODO |
| issue_history | Audit trail of issue changes | TODO |
| notifications | User notifications | TODO |
| saved_filters | Saved search filters | Done |

## Indexes

- users.email (unique)
- projects.key (unique)
- issues.key (unique)
- issues: {projectId, status}, {projectId, sprintId}, {assigneeId, status}
- comments: {issueId, createdAt}
- sprints: {projectId: 1, createdAt: -1}
- sprints.projectId (unique where status is "ACTIVE")
- saved_filters: {ownerId: 1, createdAt: -1}

## Embedded vs referenced

- Embedded: project.members, project.workflow, board.columns
- Separate collections: comments, worklogs, issue_history (unbounded growth)

## Other databases

| Technology | Use |
|---|---|
| Redis | Sessions, issue key counter, sprint locks, caching |
| ClickHouse | Analytics events and reports |
| Elasticsearch | Full-text search over issues and comments |

## ClickHouse analytics
| Column | Type | Purpose |
|---|---|---|
| event_id | String | MongoDB issue_history document ID |
| project_id | String | Project ID |
| issue_id | String | Issue ID |
| issue_key | String | Human-readable issue key |
| actor_id | String | User who performed the action |
| event_type | LowCardinality(String) | Issue history action |
| changes_json | String | JSON array of field changes |
| occurred_at_ms | Int64 | Event time as Unix milliseconds |
| occurred_at | DateTime64(3, UTC) | Materialized event timestamp |

## Report queries and cache

Reports aggregate analytics_events in ClickHouse.

- Event counts group by event_type.
- Daily activity groups by UTC event date and event_type.
- Every query filters by project_id and the requested time range.
- FINAL deduplicates repeated deliveries of the same event.
- The existing ClickHouse table schema is reused.

Redis report keys use the existing project cache revision:

| Key | TTL |
|---|---|
| tracker:cache:project:{projectId}:report-events:v1:{from}:{to}:{revision} | 60 seconds |
| tracker:cache:project:{projectId}:report-daily:v1:{from}:{to}:{revision} | 60 seconds |

Reports share the project cache revision with the existing project views.
After a successful ClickHouse event write, that revision is incremented.
Entries from older revisions expire automatically.