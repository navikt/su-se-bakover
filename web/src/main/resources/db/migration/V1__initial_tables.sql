create table sak
(
    id        bigserial	 			   primary key,
    fnr       varchar(11)              not null,
    opprettet timestamp with time zone not null default (now() at time zone 'utc')
);

create table søknad
(
    id        bigserial	 			   primary key,
    json      JSONB                    not null,
    opprettet timestamp with time zone not null default (now() at time zone 'utc')
);

create table stønadsperiode
(
	id bigserial primary key,
	sakId bigint not null references sak(id),
	søknadId bigint not null references søknad(id)
);

