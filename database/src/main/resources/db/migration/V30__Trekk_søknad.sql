alter table søknad
    add column if not exists
        trukket text
            default false;