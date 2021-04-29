create table if not exists grunnlag_uføre
(
    id uuid primary key,
    opprettet timestamptz not null,
    -- TODO jah/jacob: Knytt behandlingId mot en felles behandlingstabell hvis den blir laget
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null,
    uføregrad integer not null,
    forventetInntekt integer not null
);

create table if not exists vilkårsvurdering_uføre
(
    id uuid primary key,
    opprettet timestamptz not null,
    -- TODO jah/jacob: Knytt behandlingId mot en felles behandlingstabell hvis den blir laget
    behandlingId uuid not null,
    uføre_grunnlag_id uuid references grunnlag_uføre(id),
    vurdering text not null,
    resultat text not null,
    begrunnelse text,
    fraOgMed date not null,
    tilOgMed date not null
    );
