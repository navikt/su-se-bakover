alter table søknad
    add column if not exists
        søknadTrukket jsonb
            default null;