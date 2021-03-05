create table if not exists vedtak
(
    id                        uuid        not null primary key,
    opprettet                 timestamptz not null,
    fraOgMed                  date,
    tilOgMed                  date,
    saksbehandler             text        not null,
    attestant                 text        not null,
    behandlingsinformasjon    jsonb       not null,
    utbetalingid              varchar(30) references utbetaling(id),
    simulering                jsonb,
    beregning                 jsonb,
    iverksattjournalpostid    text,
    iverksattBrevbestillingId text
);

create table if not exists behandling_vedtak
(
    id                  uuid primary key,
    vedtakId            uuid references vedtak (id) NOT NULL,
    sakId               uuid references sak (id)    NOT NULL,
    søknadsbehandlingId uuid references behandling (id),
    revurderingId       uuid references revurdering (id),

    CONSTRAINT revurderingId_eller_søknadsbehandlingId
        CHECK (
                (søknadsbehandlingId IS NULL AND revurderingId IS NOT NULL)
                OR
                (søknadsbehandlingId IS NOT NULL AND revurderingId IS NULL)
            )
);

-- Populer vedtaktabellen (og knytningstabellen) med eksisterende søknadsbehandlinger og revurderinger
WITH b AS (
    SELECT
           b.*,
           uuid_generate_v4() vedtakId,
           vs.opprettet opprettetTidspunkt
    FROM behandling b
    JOIN vedtakssnapshot vs ON vs.behandlingid = b.id
    WHERE b.status IN ('IVERKSATT_INNVILGET', 'IVERKSATT_AVSLAG')
),
     v as (
         INSERT INTO vedtak (
                             id,
                             opprettet,
                             fraOgMed,
                             tilOgMed,
                             saksbehandler,
                             attestant,
                             behandlingsinformasjon,
                             utbetalingid,
                             simulering,
                             beregning,
                             iverksattjournalpostid,
                             iverksattBrevbestillingId
             ) (SELECT b.vedtakId,
                       b.opprettetTidspunkt,
                       (b.beregning -> 'periode' ->> 'fraOgMed')::date,
                       (b.beregning -> 'periode' ->> 'tilOgMed')::date,
                       b.saksbehandler,
                       b.attestering ->> 'attestant',
                       b.behandlingsinformasjon,
                       b.utbetalingid,
                       b.simulering,
                       b.beregning,
                       b.iverksattjournalpostid,
                       b.iverksattbrevbestillingid
                FROM b
         )
     )
INSERT
INTO behandling_vedtak(id,
                       vedtakId,
                       sakId,
                       søknadsbehandlingId,
                       revurderingId)
    (
        SELECT uuid_generate_v4(),
               b.vedtakId,
               b.sakId,
               b.id,
               NULL
        FROM b
    );


WITH r AS (
    SELECT r.*,
           behandlingsinformasjon,
           sakId,
           uuid_generate_v4() vedtakId
    FROM revurdering r
             JOIN behandling b on r.behandlingid = b.id
    WHERE r.revurderingsType = 'IVERKSATT'
),
     v AS (
         INSERT
             INTO vedtak (id,
                          opprettet,
                          fraOgMed,
                          tilOgMed,
                          saksbehandler,
                          attestant,
                          behandlingsinformasjon,
                          utbetalingid,
                          simulering,
                          beregning,
                          iverksattjournalpostid,
                          iverksattBrevbestillingId)
                 (SELECT r.vedtakId,
                         NOW(),
                         (r.beregning -> 'periode' ->> 'fraOgMed')::date,
                         (r.beregning -> 'periode' ->> 'tilOgMed')::date,
                         r.saksbehandler,
                         r.attestant,
                         r.behandlingsinformasjon,
                         r.utbetalingid,
                         r.simulering,
                         r.beregning,
                         r.iverksattjournalpostid,
                         r.iverksattbrevbestillingid
                  FROM r)
     )
INSERT
INTO behandling_vedtak(id,
                       vedtakId,
                       sakId,
                       søknadsbehandlingId,
                       revurderingId)
    (SELECT uuid_generate_v4(),
            r.vedtakId,
            r.sakId,
            NULL,
            r.id
     FROM r
    );

-- Endre revurdering-tabell: bytt ut behandlingId med vedtakId
ALTER TABLE revurdering
    ADD COLUMN vedtakSomRevurderesId uuid references vedtak (id);

UPDATE revurdering
SET vedtakSomRevurderesId = subquery.vedtakid
FROM (SELECT * FROM behandling_vedtak) AS subquery
WHERE subquery.søknadsbehandlingId = revurdering.behandlingid;

ALTER TABLE revurdering
    ALTER COLUMN vedtakSomRevurderesId SET NOT NULL;

ALTER TABLE revurdering
    DROP COLUMN behandlingid;

