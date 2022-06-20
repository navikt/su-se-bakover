create table if not exists vilkårsvurdering_familiegjenforening
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    resultat text not null,
    fraOgMed date not null,
    tilOgMed date not null
);
