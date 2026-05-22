CREATE TABLE IF NOT EXISTS regulering_status_utestaaende (
    id UUID PRIMARY KEY,
    produser_status TEXT NOT NULL,
    regulering_status JSONB
);
