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
    sakId	  bigserial 			   not null references sak(id),
    opprettet timestamp with time zone not null default (now() at time zone 'utc')
);

