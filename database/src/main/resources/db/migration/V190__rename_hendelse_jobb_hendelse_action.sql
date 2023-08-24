alter table hendelse_jobb
    RENAME TO hendelse_action;

ALTER TABLE hendelse_action
    RENAME COLUMN jobbnavn TO action;
