alter table notat add column attestant_notat text default '';

ALTER TABLE notat
    RENAME COLUMN saksbehandler TO hendelser;