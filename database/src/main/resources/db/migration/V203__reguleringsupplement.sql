CREATE TABLE IF NOT EXISTS reguleringssupplement (
    id uuid primary key default gen_random_uuid(),
    opprettet timestamptz default now(),
    supplement jsonb not null
);

ALTER TABLE
    regulering
ADD COLUMN if not exists reguleringsupplement jsonb not null default '{
  "bruker": null,
  "eps": []
}';