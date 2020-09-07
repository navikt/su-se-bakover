alter table utbetaling
    add column if not exists
        kvittering jsonb;