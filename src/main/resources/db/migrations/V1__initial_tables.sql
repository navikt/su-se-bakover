CREATE TABLE soknad
(
    id        VARCHAR(32),
    fnr       VARCHAR(32)              NOT NULL,
    json      JSONB                    NOT NULL,
    opprettet TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'utc'),
    PRIMARY KEY (id)
);

create index "index_soknad_fnr" on soknad using btree (fnr);
