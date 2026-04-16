ALTER TABLE fradragssjekk_kjoring ADD COLUMN oppsummering jsonb;

WITH saksresultater AS (
    SELECT
        fk.id AS kjoring_id,
        saksresultat,
        (saksresultat ->> 'sakId')::uuid AS sak_id,
        saksresultat ->> 'status' AS status,
        saksresultat -> 'sjekkplan' -> 'sak' ->> 'type' AS sakstype
    FROM fradragssjekk_kjoring fk
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(fk.resultat -> 'saksresultater', '[]'::jsonb)) AS saksresultat
),
opprettede_saksresultater AS (
    SELECT *
    FROM saksresultater
    WHERE status = 'OPPGAVE_OPPRETTET'
),
eksplisitte_aarsaker AS (
    SELECT DISTINCT
        sr.kjoring_id,
        sr.sak_id,
        sr.sakstype,
        avvik -> 'fradragstype' ->> 'kategori' AS fradragstype,
        NULLIF(avvik -> 'fradragstype' ->> 'beskrivelse', '') AS beskrivelse
    FROM opprettede_saksresultater sr
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(sr.saksresultat -> 'oppgaveAvvik', '[]'::jsonb)) AS avvik
    WHERE COALESCE(avvik -> 'fradragstype', 'null'::jsonb) <> 'null'::jsonb
),
fallback_aarsaker AS (
    SELECT DISTINCT
        sr.kjoring_id,
        sr.sak_id,
        sr.sakstype,
        sjekkpunkt -> 'fradragstype' ->> 'kategori' AS fradragstype,
        NULLIF(sjekkpunkt -> 'fradragstype' ->> 'beskrivelse', '') AS beskrivelse
    FROM opprettede_saksresultater sr
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(sr.saksresultat -> 'sjekkplan' -> 'sjekkpunkter', '[]'::jsonb)) AS sjekkpunkt
    WHERE jsonb_array_length(COALESCE(sr.saksresultat -> 'oppgaveAvvik', '[]'::jsonb)) = 1
      AND jsonb_array_length(COALESCE(sr.saksresultat -> 'sjekkplan' -> 'sjekkpunkter', '[]'::jsonb)) = 1
      AND NOT EXISTS(
        SELECT 1
        FROM eksplisitte_aarsaker ea
        WHERE ea.kjoring_id = sr.kjoring_id
          AND ea.sak_id = sr.sak_id
      )
),
oppgaveaarsaker AS (
    SELECT * FROM eksplisitte_aarsaker
    UNION
    SELECT * FROM fallback_aarsaker
),
grupperte_aarsaker AS (
    SELECT
        kjoring_id,
        sakstype,
        fradragstype,
        beskrivelse,
        COUNT(DISTINCT sak_id) AS antall_oppgaver
    FROM oppgaveaarsaker
    GROUP BY kjoring_id, sakstype, fradragstype, beskrivelse
),
saksresultater_per_sakstype AS (
    SELECT
        kjoring_id,
        sakstype,
        COUNT(DISTINCT sak_id) AS antall_oppgaver
    FROM opprettede_saksresultater
    GROUP BY kjoring_id, sakstype
),
beregnet_oppsummering AS (
    SELECT
        fk.id,
        jsonb_build_object(
            'antallOppgaver',
            (
                SELECT COUNT(*)
                FROM jsonb_array_elements(COALESCE(fk.resultat -> 'saksresultater', '[]'::jsonb)) AS saksresultat
                WHERE saksresultat ->> 'status' = 'OPPGAVE_OPPRETTET'
            ),
            'oppgaverPerSakstype',
            COALESCE(
                (
                    SELECT jsonb_agg(
                        jsonb_build_object(
                            'sakstype', ss.sakstype,
                            'antallOppgaver', ss.antall_oppgaver,
                            'oppgaverPerFradrag',
                            COALESCE(
                                (
                                    SELECT jsonb_agg(
                                        jsonb_build_object(
                                            'fradragstype', ga.fradragstype,
                                            'beskrivelse', ga.beskrivelse,
                                            'antallOppgaver', ga.antall_oppgaver
                                        )
                                        ORDER BY ga.antall_oppgaver DESC, ga.fradragstype, COALESCE(ga.beskrivelse, '')
                                    )
                                    FROM grupperte_aarsaker ga
                                    WHERE ga.kjoring_id = fk.id
                                      AND ga.sakstype = ss.sakstype
                                ),
                                '[]'::jsonb
                            )
                        )
                        ORDER BY ss.antall_oppgaver DESC, ss.sakstype
                    )
                    FROM saksresultater_per_sakstype ss
                    WHERE ss.kjoring_id = fk.id
                ),
                '[]'::jsonb
            )
        ) AS oppsummering
    FROM fradragssjekk_kjoring fk
)
UPDATE fradragssjekk_kjoring fk
SET oppsummering = bo.oppsummering
FROM beregnet_oppsummering bo
WHERE fk.id = bo.id;

ALTER TABLE fradragssjekk_kjoring ALTER COLUMN oppsummering SET NOT NULL;
