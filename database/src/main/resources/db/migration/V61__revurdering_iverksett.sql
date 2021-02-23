alter table revurdering
    add column if not exists
    utbetalingId varchar(30) references utbetaling(id);

alter table revurdering
    add column if not exists
    attestant text;

alter table revurdering
    add column if not exists
    iverksattjournalpostid text;

alter table revurdering
    add column if not exists
    iverksattbrevbestillingid text;
