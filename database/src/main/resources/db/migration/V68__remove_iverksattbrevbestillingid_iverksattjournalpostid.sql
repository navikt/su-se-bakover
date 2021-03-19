ALTER TABLE revurdering
    DROP COLUMN iverksattjournalpostid;

ALTER TABLE revurdering
DROP COLUMN iverksattbrevbestillingid;

ALTER TABLE behandling
DROP COLUMN iverksattjournalpostid;

ALTER TABLE behandling
DROP COLUMN iverksattbrevbestillingid;