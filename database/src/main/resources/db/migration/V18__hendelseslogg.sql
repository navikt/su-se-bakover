create table if not exists hendelseslogg
(
    id text
        primary key,
    hendelser
        jsonb
);

insert into hendelseslogg
(
 select id from behandling
);