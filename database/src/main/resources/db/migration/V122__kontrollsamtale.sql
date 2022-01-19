create table if not exists kontrollsamtale
(
    id uuid primary key,
    opprettet timestamptz not null,
    sakId uuid references sak (id) NOT NULL,
    innkallingsdato date not null,
    status text not null,
    frist date not null,
    dokumentId uuid references dokument (id)
);
