-- Vi har ikke begynt skrive til denne enda.
drop table hendelse;

create table hendelse
(
    /* Identifiserer en spesifikk hendelse og skiller den fra alle andre hendelser (også på tvers av entiter/streams). Settes av domenet. */
    hendelseId             uuid        not null primary key,

    /* En hendelse kan bli korrigert/annullert. I de tilfellene vil den nye hendelsen peke på den hendelsen den delvis eller helt erstatter. */
    tidligereHendelseId uuid unique,

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

    /* kun for auditing/tracing/debug-formål. Skal ikke inn i domenet. Domenet eier feltet hendelsestidspunkt. Dersom en type hendelser trenger flere tidspunkt/datoer legges disse inn i data-objektet. */
    persisteringstidspunkt timestamptz not null DEFAULT now(),

    /* Tidspunktet hendelsen skjedde sett fra domenet sin side. */
    hendelsestidspunkt timestamptz not null,

    /* Identifikatoren for en spesifikk hendelsesserie. Vil ofte følge aggregatene, som i de fleste tilfeller vil være sakId. Også kalt streamId/aggregateId. */
    entitetId              uuid        not null,

    /*  En hendelseserie sin rekkefølge. F.eks. 1. ny søknad -> 2. journalfør søknad -> 3. opprett oppgave */
    versjon                bigint      not null,

    /* Identifiserer en unik hendelse på samme måte som hendelseId og hendelsesnummer. Se doc på entitetId og versjon.  */
    unique (entitetId, versjon)
);

create index hendelse_type_idx on hendelse (type);
