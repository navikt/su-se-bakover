create table if not exists grunnlag_formue
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null,
    epsFormue JSONB,
    søkerFormue JSONB not null,
    begrunnelse text
);