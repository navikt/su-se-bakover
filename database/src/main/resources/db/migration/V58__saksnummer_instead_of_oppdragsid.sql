truncate
    hendelseslogg,
    utbetalingslinje,
    behandling,
    utbetaling,
    avstemming,
    oppdrag,
    søknad,
    vedtakssnapshot,
    sak
    cascade;

alter table vedtakssnapshot
    add column if not exists
        behandlingId uuid not null
            references behandling (id);

alter table utbetaling
    drop column if exists oppdragId;

alter table utbetaling
    add column if not exists
        sakId uuid not null
            references sak (id);

drop table if exists oppdrag;