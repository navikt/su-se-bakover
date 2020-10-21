alter table utbetaling
    add column if not exists
        behandler text not null;

alter table utbetaling
    add column if not exists
        avstemmingsnøkkel jsonb not null;

alter table utbetaling
    rename column oppdragsmelding to utbetalingsrequest;

alter table utbetaling
    alter column utbetalingsrequest set not null;

alter table utbetaling
    alter column simulering set not null;

alter table utbetaling
    alter column type set not null;