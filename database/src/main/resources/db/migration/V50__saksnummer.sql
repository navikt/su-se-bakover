alter table sak add column saksnummer bigint generated by default as identity (start with 2021) not null unique
