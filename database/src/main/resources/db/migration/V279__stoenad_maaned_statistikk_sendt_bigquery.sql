-- Failover-flagg for oversendelse til BigQuery.
-- false = ikke sendt enda, true = sendt.
alter table stoenad_maaned_statistikk
    add column sendt_bigquery boolean not null default false;

-- Alle eksisterende rader er allerede sendt til BigQuery i dagens flyt.
-- Vi backfiller dem som sendt slik at draineren ikke sender hele historikken på nytt.
update stoenad_maaned_statistikk
set sendt_bigquery = true;

-- Partielt indeks for rask oppslag av usendte rader per måned.
create index idx_stoenad_maaned_statistikk_usendt
    on stoenad_maaned_statistikk (maaned)
    where sendt_bigquery = false;
