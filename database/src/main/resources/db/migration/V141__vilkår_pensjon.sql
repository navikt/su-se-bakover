create table if not exists grunnlag_pensjon
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null
);

create table if not exists vilkårsvurdering_pensjon
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    grunnlag_id uuid references grunnlag_pensjon(id),
    resultat text not null,
    fraOgMed date not null,
    tilOgMed date not null
);
