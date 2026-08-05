alter table notat add column attestant_notat text;

ALTER TABLE notat
    RENAME COLUMN saksbehandler TO hendelser;