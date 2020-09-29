alter table beregning
    add column if not exists
        forventetInntekt integer not null default 0;