alter table hendelse_action
    RENAME TO hendelse_konsument;

ALTER TABLE hendelse_konsument
    RENAME COLUMN action TO konsumentId;

-- Dette er mer en safeguard. Skal egentlig ivaretas av domenet.
CREATE UNIQUE INDEX idx_unique_hendelseid_konsumentid
    ON hendelse_konsument (hendelseId, konsumentId);

ALTER TABLE hendelse DROP COLUMN triggetAv;
