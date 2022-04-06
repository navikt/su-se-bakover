create table if not exists job_context
(
    id text primary key,
    context json not null
);