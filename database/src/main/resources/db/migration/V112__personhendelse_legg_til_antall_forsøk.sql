ALTER TABLE personhendelse
    ADD COLUMN IF NOT EXISTS
      antallFeiledeForsøk int not null default 0
