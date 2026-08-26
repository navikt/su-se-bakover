create table if not exists jobb_kjoring (
    id uuid primary key,
    jobb_navn text not null,
    status text not null,
    startet_tidspunkt timestamptz not null,
    ferdig_tidspunkt timestamptz,
    feilmelding text,
    intervall_sekunder bigint not null
);

create index idx_jobb_kjoring_jobb_navn_startet on jobb_kjoring (jobb_navn, startet_tidspunkt desc);
