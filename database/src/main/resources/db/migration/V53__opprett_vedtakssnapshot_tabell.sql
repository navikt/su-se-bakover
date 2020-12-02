create table if not exists vedtakssnapshot
(
    id text
        primary key,
    opprettet timestamp with time zone
        not null,
    vedtakstype text
        not null,
    json jsonb
        not null
);