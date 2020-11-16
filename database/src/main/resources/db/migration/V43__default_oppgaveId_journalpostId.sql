update søknad set journalpostId = 'ukjent' where journalpostId is null;
update søknad set oppgaveId  = 'ukjent' where oppgaveId is null;
update behandling set oppgaveId = (select oppgaveId from søknad s where s.id = søknadId);