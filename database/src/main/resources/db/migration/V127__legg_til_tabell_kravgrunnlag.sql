create table if not exists tilbakekrevingsbehandling
(
    id                 uuid primary key,
    opprettet          timestamptz not null,
    sakId              uuid        not null references sak (id),
    revurderingId      uuid        not null references revurdering (id),
    fraOgMed           date        not null,
    tilOgMed           date        not null,
    avgjørelse         text        not null,
    tilstand           text        not null,
    kravgrunnlag       text        null,
    kravgrunnlagMottatt timestamptz null,
    tilbakekrevingsvedtakForsendelse   text      null
);
