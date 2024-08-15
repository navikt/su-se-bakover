-- Klager kan også peke på vedtak utenfor vedtasdatabasen, I.E tilbakekrevingsvedtak er i hendelsesdatabasen.
ALTER TABLE klage
    DROP CONSTRAINT IF EXISTS klage_vedtakid_fkey;
