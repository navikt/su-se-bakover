create table if not exists behandling
(
    id bigserial primary key,
    stønadsperiodeId bigint not null references stønadsperiode(id)
);