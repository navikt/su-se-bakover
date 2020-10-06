alter table søknad
    add column if not exists
        avsluttetBegrunnelse text
            default null;

alter table behandling
    add column if not exists
        avsluttetBegrunnelse text
            default null;
