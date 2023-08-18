ALTER TABLE hendelse
    ADD triggetAv uuid REFERENCES hendelse (hendelseId) default null;





