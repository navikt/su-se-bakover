alter table utbetaling
    add column if not exists
        oppdragsmelding jsonb;