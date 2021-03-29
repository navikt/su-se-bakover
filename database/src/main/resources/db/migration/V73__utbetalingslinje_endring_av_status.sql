alter table utbetalingslinje drop constraint utbetalingslinje_forrigeutbetalingslinjeid_fkey;
alter table utbetalingslinje drop constraint utbetalingslinje_pkey;

alter table utbetalingslinje add column if not exists internid uuid primary key default uuid_generate_v4();

alter table utbetalingslinje add column if not exists status text;
alter table utbetalingslinje add column if not exists statusFraOgMed date;