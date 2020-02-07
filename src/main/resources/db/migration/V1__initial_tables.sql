CREATE TABLE soknad
(
    id        BIGSERIAL	 			   PRIMARY KEY,
    json      JSONB                    NOT NULL,
    opprettet TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'utc')
);

