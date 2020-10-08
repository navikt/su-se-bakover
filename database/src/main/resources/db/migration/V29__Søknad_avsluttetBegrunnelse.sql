alter table søknad
    add column if not exists
        avsluttetBegrunnelse text
            default null;
