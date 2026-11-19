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

## Elasticsearch issue index

The tracker-issues-v1 index is defined in
docker/elasticsearch/issues-index.json.

| Field | Mapping |
|---|---|
| projectId | keyword |
| deleted | boolean |
| key | text with a lowercase keyword subfield named exact |
| title | search_as_you_type |
| description | text |

The Elasticsearch document ID is the MongoDB issue ID.

The search_pending index on MongoDB issue_history covers
searchIndexed, createdAt and _id.

History entries without searchIndexed=true are processed in batches of 100.
Each entry loads the current issue before indexing it.
Successful indexing marks that history entry as processed.

Document versions use the MongoDB issue version plus one.
External version checks reject stale updates.

Deleted issues are replaced by a deleted=true marker with the maximum
signed long version. Their document IDs must not be reused.
Search queries exclude deletion markers.

Existing issue history is also eligible for indexing.
The export markers assume that the configured Elasticsearch index retains
its documents. Rebuilding a lost index requires replaying history by clearing
the searchIndexed markers in the corresponding MongoDB database.

## User data archive
Data export reads existing MongoDB collections.
It does not create an export collection.

Selection rules:

- projects: members.userId matches the authenticated user.
- issues: reporterId or assigneeId matches the user, and the project
  is currently accessible to the user.
- comments: authorId matches the user.
- worklogs: userId matches the user.
- saved_filters: ownerId matches the user.
- notifications: userId matches the user.
- invitations: invitedUserId or invitedBy matches the user.
- issue_history: actorId matches the user.

Every collection uses an explicit inclusion projection.
The user profile is mapped through UserResponse.
Only the current user's project membership element is exported.

The archive format is versioned through manifest.json.