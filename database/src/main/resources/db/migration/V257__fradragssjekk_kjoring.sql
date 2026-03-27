create table if not exists fradragssjekk_kjoring (
    id uuid primary key,
    dato date not null,
    status varchar not null,
    opprettet timestamptz not null,
    ferdigstilt timestamptz null,
    resultat jsonb not null,
    feilmelding text null
);
