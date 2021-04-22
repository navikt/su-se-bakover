create table if not exists vilkårsvurdering_uføre
(
    id uuid primary key,
    opprettet timestamptz not null,
    behandlingId uuid not null,
    uføre_grunnlag_id uuid references grunnlag_uføre(id) on delete cascade,
    vurdering text not null,
    resultat text not null,
    begrunnelse text
);