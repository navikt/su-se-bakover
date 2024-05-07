UPDATE regulering
SET arsakForManuell = jsonb_set(COALESCE(arsakForManuell, '[]'::jsonb), '{}', '[]'::jsonb)
WHERE arsakForManuell IS NULL;