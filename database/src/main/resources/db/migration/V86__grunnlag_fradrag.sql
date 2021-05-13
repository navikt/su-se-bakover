create table if not exists grunnlag_fradrag
(
    id uuid primary key,
    opprettet timestamptz not null,
    -- TODO jah/jacob: Knytt behandlingId mot en felles behandlingstabell hvis den blir laget
    behandlingId uuid not null,
    fraOgMed date not null,
    tilOgMed date not null,
    fradragstype text not null,
    månedsbeløp integer not null,
    utenlandskInntekt jsonb,
    tilhører text not null
);