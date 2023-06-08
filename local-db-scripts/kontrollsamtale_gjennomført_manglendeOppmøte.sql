/**
  Henter ut det totale antallet kontrollsamtaler som ikke er møtt, og som er gjennomført.
  Henter ut totale antall iverksatte stans, og iverksatte gjenopptak
 */
with gjennomFørteOgIkkeGjennomførteKontrollsamteler as (
    select status, count(status) from kontrollsamtale WHERE status IN ('IKKE_MØTT_INNEN_FRIST', 'GJENNOMFØRT') group by status
),
     antallIverksattStans as (
         select revurderingstype, count(*) from revurdering where revurderingstype = 'IVERKSATT_STANS' group by revurderingstype
     ),
     antallIverksattGjenopptak as (
         select revurderingstype, count(*) from revurdering where revurderingstype = 'IVERKSATT_GJENOPPTAK' group by revurderingstype
     )
select * from gjennomFørteOgIkkeGjennomførteKontrollsamteler
union all
select * from antallIverksattStans
union all
select * from antallIverksattGjenopptak;

/**
  Samme som over, men er delt på per måned
 */
WITH kontrollsamtaler AS (
    SELECT
        DATE_TRUNC('month', opprettet) AS month_year,
        status,
        COUNT(status) AS count
    FROM kontrollsamtale
    WHERE status IN ('IKKE_MØTT_INNEN_FRIST', 'GJENNOMFØRT')
      AND DATE_TRUNC('month', opprettet) BETWEEN '2021-01-01' AND '2023-06-30'
    GROUP BY month_year, status
),
     revurdering AS (
         SELECT
             DATE_TRUNC('month', (periode->>'fraOgMed')::date) AS month_year,
             revurderingstype,
             COUNT(*) AS count
         FROM revurdering
         WHERE revurderingstype IN ('IVERKSATT_STANS', 'IVERKSATT_GJENOPPTAK')
           AND DATE_TRUNC('month', (periode->>'fraOgMed')::date) BETWEEN '2021-01-01' AND '2023-06-30'
         GROUP BY month_year, revurderingstype
     ),
     combined AS (
         SELECT * FROM kontrollsamtaler
         UNION ALL
         SELECT * FROM revurdering
     )
SELECT
    month_year,
    COALESCE(SUM(CASE WHEN status = 'GJENNOMFØRT' THEN count END), 0) AS gjennomført_count,
    COALESCE(SUM(CASE WHEN status = 'IKKE_MØTT_INNEN_FRIST' THEN count END), 0) AS ikke_møtt_innen_frist_count,
    COALESCE(SUM(CASE WHEN status = 'IVERKSATT_STANS' THEN count END), 0) AS iverksatt_stans_count,
    COALESCE(SUM(CASE WHEN status = 'IVERKSATT_GJENOPPTAK' THEN count END), 0) AS iverksatt_gjenopptak_count
FROM combined
GROUP BY month_year
ORDER BY month_year;