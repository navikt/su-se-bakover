alter table utbetaling
    add column if not exists
        behandler text not null;

alter table utbetaling
    add column if not exists
        avstemmingsnøkkel text not null;