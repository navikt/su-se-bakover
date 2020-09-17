create table if not exists hendelseslogg
(
    id text
        primary key,
    hendelser
        jsonb
);