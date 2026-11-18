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
| saved_filters | Saved search filters | TODO |

## Indexes

- users.email (unique)
- projects.key (unique)
- issues.key (unique)
- issues: {projectId, status}, {projectId, sprintId}, {assigneeId, status}
- comments: {issueId, createdAt}
- sprints: {projectId: 1, createdAt: -1}
- sprints.projectId (unique where status is "ACTIVE")

## Embedded vs referenced

- Embedded: project.members, project.workflow, board.columns
- Separate collections: comments, worklogs, issue_history (unbounded growth)

## Other databases

| Technology | Use |
|---|---|
| Redis | Sessions, issue key counter, sprint locks, caching |
| ClickHouse | Analytics events and reports |
| Elasticsearch | Full-text search over issues and comments |