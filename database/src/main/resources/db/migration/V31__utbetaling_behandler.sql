alter table utbetaling
    add column if not exists
        behandler text
            not null default 'Z993156';