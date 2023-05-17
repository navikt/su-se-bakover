CREATE TABLE IF NOT EXISTS
    dokument_skatt
(
    id               uuid                        not null primary key,
    generertDokument bytea                       not null,
    dokumentJson     jsonb                       not null,
    sakId            uuid references sak (id)    not null,
    søkersSkatteId   uuid references skatt (id)  not null,
    epsSkatteId      uuid references skatt (id) default null,
    vedtakId         uuid references vedtak (id) not null,
    journalpostId    text                       default null
);