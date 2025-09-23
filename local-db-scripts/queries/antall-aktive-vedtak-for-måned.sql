WITH siste_vedtak_per_sak AS (
    SELECT DISTINCT ON (sakid)
        sakid,
        opprettet,
        vedtaktype
    FROM vedtak
    WHERE opprettet < '2025-09-18 11:00:00.000000 +00:00'
        AND fraogmed <= '2025-08-01'
        AND tilogmed >= '2025-08-31'
        AND vedtaktype IN ('SØKNAD','ENDRING', 'OPPHØR','REGULERING')
    ORDER BY sakid, opprettet DESC
)
SELECT COUNT(DISTINCT sakid)
FROM siste_vedtak_per_sak
WHERE vedtaktype != 'OPPHØR';