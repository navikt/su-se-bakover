ALTER TABLE
    regulering
    ADD COLUMN IF NOT EXISTS
        supplement jsonb not null DEFAULT '{
          "bruker": null,
          "eps": []
        }'::jsonb;