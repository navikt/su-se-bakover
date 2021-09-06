create table if not exists konsistensavstemming
(
    id varchar(30)
        primary key,
    opprettet timestamp with time zone
        not null,
    løpendeFraOgMed
        timestamp with time zone,
    opprettetTilOgMed
        timestamp with time zone,
    utbetalinger
        json,
    avstemmingXmlRequest
        text
);