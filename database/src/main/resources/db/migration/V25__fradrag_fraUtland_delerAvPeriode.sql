alter table fradrag
    add column if not exists
        utenlandskInntekt jsonb
            default null;

alter table fradrag
    add column if not exists
        inntektDelerAvPeriode jsonb
            default null;

alter table fradrag
    drop column if exists beskrivelse;
