ALTER TABLE hendelse
    ADD COLUMN IF NOT EXISTS triggetAv uuid REFERENCES hendelse (hendelseId) default null;





