alter table klagevedtak
    add column if not exists utlest_utfall text default null, -- Tilsvarer utfall i metadata
    add column if not exists utlest_journalpostid text default null, -- Tilsvarer vedtaksbrevReferanse i metadata
    add column if not exists utlest_klageid uuid default null references klage(id), -- Tilsvarer kildeReferanse i metadata
    add column if not exists utlest_eventid text default null; -- Tilsvarer eventId i metadata
