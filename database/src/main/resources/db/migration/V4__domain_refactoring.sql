drop table if exists vilkårsvurdering;
drop table if exists behandling;
drop table if exists stønadsperiode;
drop table if exists søknad;
drop table if exists sak;
drop type if exists  vilkår_vurdering_status;
drop type if exists  vilkår;

create EXTENSION IF NOT EXISTS "uuid-ossp";

create table if not exists sak
(
    id uuid
        primary key,
    fnr varchar(11)
        not null,
    opprettet timestamp with time zone
        not null
);

create table if not exists søknad
(
    id uuid
        primary key,
    sakId uuid
        not null references sak(id),
    søknadInnhold JSONB
        not null,
    opprettet timestamp with time zone
        not null
);

create table if not exists behandling
(
    id uuid
        primary key,
    sakId uuid
        not null references sak(id),
    søknadId uuid
        not null references søknad(id),
    opprettet timestamp with time zone
        not null
);

create table if not exists vilkårsvurdering
(
    id uuid
        primary key,
    behandlingId uuid
        not null references behandling(id),
    vilkår text
        not null,
    begrunnelse text,
    status text
        not null,
    opprettet timestamp with time zone
        not null
);
