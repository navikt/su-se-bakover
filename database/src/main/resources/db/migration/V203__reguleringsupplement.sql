CREATE TABLE IF NOT EXISTS reguleringssupplement (
    id uuid primary key,
    opprettet timestamptz not null,
    supplement jsonb not null
);

ALTER TABLE
    regulering
ADD COLUMN if not exists reguleringsupplement jsonb not null default '{
  "supplementId": null,
  "bruker": null,
  "eps": []
}';

ALTER TABLE
    regulering
ALTER COLUMN
    reguleringsupplement
DROP DEFAULT;