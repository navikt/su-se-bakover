create table hendelse_jobb
(
    id         uuid primary key,
    hendelseId uuid REFERENCES hendelse (hendelseId),
    jobbNavn   text
)
