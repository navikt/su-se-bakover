alter table søknad
    add column if not exists
        lukket jsonb
            default null;