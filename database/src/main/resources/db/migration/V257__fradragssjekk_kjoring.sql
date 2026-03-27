create table if not exists fradragssjekk_kjoring (
    id uuid primary key,
    maaned date not null,
    status varchar not null,
    opprettet timestamptz not null,
    ferdigstilt timestamptz null,
    resultat jsonb not null,
    feilmelding text null
);

create index if not exists idx_fradragssjekk_kjoring_status_maaned
    on fradragssjekk_kjoring(status, maaned);
