create table sak
(
    id        bigserial	 			   primary key,
    fnr       varchar(11)              not null,
    opprettet timestamp with time zone not null default (now() at time zone 'utc')
);

