create table if not exists grunnlag_lovligopphold
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null
);

create table if not exists vilkårsvurdering_lovligOpphold
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    grunnlag_id uuid references grunnlag_lovligopphold(id),
    resultat text not null,
    fraOgMed date not null,
    tilOgMed date not null,
    begrunnelse text
);
