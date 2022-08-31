alter table utbetalingslinje add column if not exists statusTilOgMed date;

update utbetalingslinje set statusTilOgMed = tom where status is not null and statusFraOgMed is not null;

alter table utbetaling drop column if exists type;
