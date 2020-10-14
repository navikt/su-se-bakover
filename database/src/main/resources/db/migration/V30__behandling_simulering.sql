alter table behandling
    add column if not exists
        simulering jsonb
            default null;