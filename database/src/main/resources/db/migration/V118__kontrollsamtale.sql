create table if not exists kontrollsamtale
(
    id uuid primary key,
    opprettet timestamptz not null,
    sakId uuid references sak (id) NOT NULL,
    innkallingsdato date,
    status text not null,
    frist date,
    dokumentId uuid references dokument (id)
);
