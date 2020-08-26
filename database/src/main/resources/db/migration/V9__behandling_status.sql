alter table behandling
    add column if not exists
        status text
            not null default 'OPPRETTET';