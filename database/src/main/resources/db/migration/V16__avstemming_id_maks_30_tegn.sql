drop table if exists avstemming;

create table if not exists avstemming
(
    id varchar(30)
        primary key,
    opprettet timestamp with time zone
        not null,
    fom
        timestamp with time zone,
    tom
        timestamp with time zone,
    utbetalinger
        jsonb,
    avstemmingXmlRequest
        text
);

alter table utbetaling
    drop column if exists avstemmingId;
alter table utbetaling
    add column if not exists
      avstemmingId varchar(30)
        references avstemming(id);