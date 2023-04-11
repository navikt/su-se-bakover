ALTER TABLE
    utbetalingslinje
ADD COLUMN IF NOT EXISTS
    rekkefølge bigint;

CREATE INDEX utbetalingslinje_rekkefølge_index ON utbetalingslinje (rekkefølge ASC NULLS LAST);
CREATE INDEX utbetalingslinje_opprettet_index ON utbetalingslinje (opprettet ASC NULLS LAST);