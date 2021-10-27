create table if not exists kravgrunnlag
(
    id        uuid primary key,
    opprettet timestamptz not null,
    melding   text        not null,
    type      text        not null
);

create table if not exists tilbakekrevingsavgjørelse
(
    id                 uuid primary key,
    opprettet          timestamptz not null,
    sakId              uuid        not null references sak (id),
    revurderingId      uuid        not null references revurdering (id),
    fraOgMed           date        not null,
    tilOgMed           date        not null,
    oversendtTidspunkt timestamptz null,
    type               text        not null
);
