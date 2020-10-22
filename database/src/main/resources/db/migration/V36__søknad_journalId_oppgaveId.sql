alter table søknad
    add column if not exists
        journalpostId text
            default null;

alter table søknad
    add column if not exists
        oppgaveId text
            default null;