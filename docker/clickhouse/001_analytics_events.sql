CREATE TABLE IF NOT EXISTS analytics_events
(
    event_id String,
    project_id String,
    issue_id String,
    issue_key String,
    actor_id String,
    event_type LowCardinality(String),
    changes_json String,
    occurred_at_ms Int64,
    occurred_at DateTime64(3, 'UTC')
    MATERIALIZED fromUnixTimestamp64Milli(occurred_at_ms, 'UTC')
    )
    ENGINE = ReplacingMergeTree
    PARTITION BY toYYYYMM(occurred_at)
    ORDER BY (project_id, event_id);