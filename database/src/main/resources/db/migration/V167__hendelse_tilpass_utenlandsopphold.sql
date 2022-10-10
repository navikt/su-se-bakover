-- Vi har ikke begynt skrive til denne enda.
drop table hendelse;

create table hendelse
(
    /* Identifiserer en spesifikk hendelse og skiller den fra alle andre hendelser (også på tvers av entiter/streams). Settes av domenet. */
    hendelseId             uuid        not null primary key,

    /*  Rekkefølgen av alle hendelser, også på tvers av entiteter. I praksis vil rekkefølgen styres av insert-tidspunkt og ikke av commit-tidspunkt. (enitetId + versjon) vil være master for rekkefølge innenfor en entitet. */
    hendelsesnummer        bigint generated always as identity unique,

    /* sakId vil i de fleste tilfeller være entitetId, men det vil finnes uavhengige aggregater som f.eks. statistikk/dokument distribusjon/oppdrag/avstemming/satser */
    sakId                  uuid references sak (id),

    /* identifiserer typen hendelse, f.eks. SAK_NY, SØKNAD_REGISTRERT.  Kan brukes for å filtrere og deserialisere. */
    type                   text        not null,

    /* den serialiserte hendelsen */
    data                   jsonb       not null,

    /* metadata relatert til hendelsen for auditing/tracing/debug-formål (f.eks. Correlation-Id, ident, brukerrettigheter) */
    meta                   jsonb       not null,

    /* kun for auditing/tracing/debug-formål. Skal ikke inn i domenet. Domenet bør lagre tidspunkter i jsonb. */
    persisteringstidspunkt timestamptz not null DEFAULT now(),

    /* Tidspunktet hendelsen skjedde sett fra domenet sin side. */
    hendelsestidspunkt timestamptz not null,

    /* Også kalt streamId. Vil ofte følge aggregatene, som i de fleste tilfeller vil være sakId. */
    entitetId              uuid        not null,

    /*  En hendelseserie sin rekkefølge. F.eks. 1. ny søknad -> 2. journalfør søknad -> 3. opprett oppgave */
    versjon                bigint      not null,

    /*  En hendelsesserie sin rekkefølge. F.eks. 1. ny sak 2. ny søknad -> 3. journalfør søknad -> 4. opprett oppgave -> 5. start søknadsbehandling */
    unique (entitetId, versjon)
);

create unique index hendelse_type_idx on hendelse (type);
