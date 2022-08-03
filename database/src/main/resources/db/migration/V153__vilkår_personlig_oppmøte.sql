create table if not exists grunnlag_personlig_oppmøte
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null,
    årsak text not null
);

create table if not exists vilkårsvurdering_personlig_oppmøte
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    grunnlag_id uuid references grunnlag_personlig_oppmøte(id),
    resultat text not null,
    fraOgMed date not null,
    tilOgMed date not null
);