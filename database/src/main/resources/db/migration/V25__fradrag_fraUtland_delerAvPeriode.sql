alter table fradrag
    add column if not exists
        fraUtlandInntekt jsonb
            default null;

alter table fradrag
    add column if not exists
        delerAvPeriode jsonb
            default null;

alter table fradrag
    drop column if exists beskrivelse;
