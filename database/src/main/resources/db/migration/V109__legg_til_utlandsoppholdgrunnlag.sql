create table if not exists grunnlag_utland
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null
);

create table if not exists vilkårsvurdering_utland
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    grunnlag_utland_id uuid references grunnlag_utland(id),
    resultat text not null,
    fraOgMed date not null,
    tilOgMed date not null,
    begrunnelse text
);
