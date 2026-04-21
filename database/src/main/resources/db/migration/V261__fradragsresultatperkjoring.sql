create table if not exists fradragssjekk_resultat_per_kjoring (
    kjoring_id uuid not null,
    sak_id uuid not null,
    dato date not null,
    opprettet timestamptz not null,
    resultat jsonb not null,
    primary key (kjoring_id, sak_id)
);

create index if not exists idx_fradragssjekk_resultat_per_kjoring_kjoring_id
    on fradragssjekk_resultat_per_kjoring (kjoring_id);


ALTER TABLE fradragssjekk_kjoring
DROP COLUMN IF EXISTS resultat;
