alter table søknad
    add column if not exists
        trukket text
            default false;

alter table søknad
    add column if not exists
        sakId uuid
            default null;
