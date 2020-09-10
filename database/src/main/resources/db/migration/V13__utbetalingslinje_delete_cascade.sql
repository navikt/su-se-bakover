alter table utbetalingslinje
    drop constraint utbetalingslinje_utbetalingid_fkey;

alter table utbetalingslinje
    add foreign key (utbetalingId) references utbetaling(id) on delete cascade;