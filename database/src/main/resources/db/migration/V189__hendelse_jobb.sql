CREATE TABLE IF NOT EXISTS hendelse_jobb
(
    id         uuid primary key,
    hendelseId uuid REFERENCES hendelse (hendelseId),
    jobbNavn   text
)
