create table if not exists grunnlag_uføre
(
    id uuid primary key,
    opprettet timestamptz not null,
    fom date,
    tom date,
    uføregrad integer not null,
    forventetInntekt integer not null
);

create table if not exists behandling_grunnlag
(
    behandlingId uuid not null,
    uføre_grunnlag_id uuid references grunnlag_uføre(id) on delete cascade
)
