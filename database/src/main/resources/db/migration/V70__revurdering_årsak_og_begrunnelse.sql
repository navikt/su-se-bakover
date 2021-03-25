alter table revurdering
    add column if not exists
    årsak text default 'MIGRERT';

alter table revurdering
    add column if not exists
    begrunnelse text default 'MIGRERT';
