alter table
    revurdering
add column
    if not exists
        vedtakSomRevurderesMånedsvis jsonb NOT NULL DEFAULT '{"måneder":[]}'::jsonb;