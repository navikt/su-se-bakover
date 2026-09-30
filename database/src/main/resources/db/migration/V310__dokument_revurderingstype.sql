ALTER TABLE dokument ADD COLUMN revurderingstype TEXT;

UPDATE dokument SET revurderingstype = 'ORDINAER' WHERE revurderingId IS NOT NULL;

ALTER TABLE dokument
    DROP CONSTRAINT dokument_revurderingid_fkey,
    ADD CONSTRAINT dokument_revurderingstype_check CHECK (
        (revurderingId IS NULL AND revurderingstype IS NULL)
        OR (
            revurderingId IS NOT NULL
            AND revurderingstype IS NOT NULL
            AND revurderingstype IN ('ORDINAER', 'HISTORISK_INFOTRYGD')
        )
    );

CREATE INDEX dokument_revurderingstype_id_idx ON dokument (revurderingstype, revurderingId)
    WHERE revurderingId IS NOT NULL;

-- Referansen peker til en av to tabeller, avhengig av revurderingstype.
CREATE FUNCTION kontroller_dokument_revurderingsreferanse() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.revurderingId IS NULL THEN
        RETURN NEW;
    END IF;
    -- Eldre appinstanser skriver ordinære referanser uten type under utrulling.
    IF NEW.revurderingstype IS NULL THEN
        NEW.revurderingstype := 'ORDINAER';
    END IF;
    IF NEW.revurderingstype = 'ORDINAER' THEN
        PERFORM id FROM revurdering WHERE id = NEW.revurderingId FOR KEY SHARE;
    ELSIF NEW.revurderingstype = 'HISTORISK_INFOTRYGD' THEN
        PERFORM id FROM historisk_infotrygd_revurdering WHERE id = NEW.revurderingId FOR KEY SHARE;
    ELSE
        RAISE EXCEPTION 'Ugyldig revurderingstype' USING ERRCODE = '23514';
    END IF;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Dokumentet refererer til en revurdering som ikke finnes' USING ERRCODE = '23503';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER dokument_revurderingsreferanse
    BEFORE INSERT OR UPDATE OF revurderingId, revurderingstype ON dokument
    FOR EACH ROW EXECUTE FUNCTION kontroller_dokument_revurderingsreferanse();

CREATE FUNCTION beskytt_dokumentets_revurdering() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id = OLD.id THEN
            RETURN NEW;
        END IF;
    END IF;
    IF EXISTS (
        SELECT 1 FROM dokument WHERE revurderingId = OLD.id AND revurderingstype = TG_ARGV[0]
    ) THEN
        RAISE EXCEPTION 'Revurderingen har tilknyttede dokumenter' USING ERRCODE = '23503';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER beskytt_ordinaer_dokumentreferanse
    BEFORE DELETE OR UPDATE OF id ON revurdering
    FOR EACH ROW EXECUTE FUNCTION beskytt_dokumentets_revurdering('ORDINAER');

CREATE TRIGGER beskytt_historisk_dokumentreferanse
    BEFORE DELETE OR UPDATE OF id ON historisk_infotrygd_revurdering
    FOR EACH ROW EXECUTE FUNCTION beskytt_dokumentets_revurdering('HISTORISK_INFOTRYGD');

UPDATE mottaker
SET referanse_type = 'HISTORISK_INFOTRYGD_REVURDERING'
WHERE referanse_type = 'REVURDERING'
  AND EXISTS (SELECT 1 FROM historisk_infotrygd_revurdering r WHERE r.id = mottaker.referanse_id);
