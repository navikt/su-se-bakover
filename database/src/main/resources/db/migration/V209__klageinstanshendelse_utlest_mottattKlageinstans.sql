alter table klageinstanshendelse
    add column if not exists utlest_mottattKlageinstans text default null -- Tilsvarer mottattKlageinstans i metadata
