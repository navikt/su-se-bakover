# kontrollsamtale

## Innsending av kontrollnotat

`POST /saker/{sakId}/kontrollsamtaler/notat` krever `id` som en
UUID-streng i request-body. Frontend skal lage ID-en én gang per nytt notat og
gjenbruke den ved dobbeltklikk og retry. Backend genererer ikke notat-ID.

Samme ID på samme sak gir `200 OK` uten ny lagring, journalføring eller
Gosys-oppgave. Eksisterende innhold beholdes uten innholdssammenligning.
Samme ID på en annen sak gir `409 Conflict`, og en ugyldig UUID gir
`400 Bad Request`. Nye notater på samme sak skal ha nye ID-er.

Journalføringsjobben forsøker fortsatt å journalføre lagrede notater uten
journalpost-ID. Gosys-oppgave opprettes bare dersom saken ikke har en planlagt
eller innkalt kontrollsamtale. Feilet oppgaveopprettelse etter lagret journalpost-ID
har fortsatt ingen retry i denne flyten.
