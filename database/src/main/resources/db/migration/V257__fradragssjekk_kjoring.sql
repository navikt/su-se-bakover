create table if not exists fradragssjekk_kjoring (
    id uuid primary key,
    dato date not null,
    status varchar not null,
    opprettet timestamptz not null,
    ferdigstilt timestamptz null,
    resultat jsonb not null,
    feilmelding text null,
    dry_run boolean
);

create unique index if not exists fradragssjekk_kjoring_unik_aar_maaned_for_ikke_dry_run
    on fradragssjekk_kjoring (
                              extract(year from dato),
                              extract(month from dato)
        )
    where dry_run = false;
