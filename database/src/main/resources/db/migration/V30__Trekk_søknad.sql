alter table søknad
    add column if not exists
        trukket jsonb
            default null;