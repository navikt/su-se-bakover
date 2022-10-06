-- En tidligere versjon av hendelser, som er fjernet fra domenet.
drop table hendelseslogg;

create table if not exists hendelse
(
    id uuid not null primary key,
    hendelsesnummer bigserial not null unique, -- bestemmer rekkefølgen av alle eventer, på tvers av domeneområder. Det vil være rekkefølgen til start transaction og ikke commit transaction.
    sakId uuid not null references sak(id), -- starter i førsteomgang å lagre sakshendelser. Utvides med kolonner for søknadId, behandlingId etc.
    type text not null, -- identifiserer typen hendelse, f.eks. SAK_NY, SØKNAD_REGISTRERT.  Kan brukes for å filtrere og deserialisere.
    data jsonb not null, -- den serialiserte hendelsen
    meta jsonb not null, -- metadata relatert til hendelsen for auditing/tracing/debug-formål (f.eks. Correlation-Id, ident, brukerrettigheter)
    tidspunkt timestamptz not null DEFAULT now(), -- kun for auditing/tracing/debug-formål. Skal ikke inn i domenet. Domenet bør lagre tidspunkter i jsonb.
    entityId uuid not null, -- Eksempler: sakId, behandlingId, revurderingId, reguleringId, oppgaveId, journalpostId, statistikkId
    version bigint not null, -- En hendelseserie sin rekkefølge. F.eks. 1. ny søknad -> 2. journalfør søknad -> 3. opprett oppgave
    unique(entityId,version) -- Dersom 2 hendelser er avhengig av hverandre i den forstand at de ikke bør skjer samtidig, bør de være knyttet til samme entityId. Eksempelvis: ny søknadsbehandling og ny revurdering bør ha streamId == sakId.
);

create unique index hendelse_type_idx on hendelse (type);