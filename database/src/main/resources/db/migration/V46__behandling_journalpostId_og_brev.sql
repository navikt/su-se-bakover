alter table behandling
    add column if not exists
        iverksattJournalpostId text;

alter table behandling
    add column if not exists
        iverksattBrevbestillingId text;