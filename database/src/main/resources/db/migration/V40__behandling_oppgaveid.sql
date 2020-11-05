alter table behandling
    add column if not exists
        oppgaveId text;

update behandling set oppgaveId = (select oppgaveId from søknad s where s.id = søknadId);

alter table behandling
    alter column oppgaveId set not null;

