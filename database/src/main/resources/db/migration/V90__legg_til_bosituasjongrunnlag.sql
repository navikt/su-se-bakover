create table if not exists grunnlag_bosituasjon
(
    id uuid primary key,
    opprettet timestamptz not null,
    -- TODO jah/jacob: Knytt behandlingId mot en felles behandlingstabell hvis den blir laget
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null,
    bosituasjontype text not null,
    eps_fnr text,
    begrunnelse text
);