UPDATE hendelse
SET data = jsonb_set(data - 'opprettetAv', '{utførtAv}', data->'opprettetAv')
WHERE type = 'OPPRETTET_TILBAKEKREVINGSBEHANDLING' AND data ? 'opprettetAv';

-- Verifikasjon: https://sqlfiddle.com/postgresql/online-compiler?id=5773ea73-7bb4-4d58-bb1b-ca0768297253
/*
CREATE TABLE hendelse (
    id SERIAL PRIMARY KEY,
    type VARCHAR(255),
    data JSONB
);

INSERT INTO hendelse (type, data) VALUES
('OPPRETTET_TILBAKEKREVINGSBEHANDLING', '{"opprettetAv": "skalEndres"}'),
('OPPRETTET_TILBAKEKREVINGSBEHANDLING', '{"utførtAv": "skalIkkeEndres"}'),
('ANOTHER_TYPE', '{"opprettetAv": "skalIkkeEndres"}'),
('OPPRETTET_TILBAKEKREVINGSBEHANDLING', '{"opprettetAv": "skalEndres", "skalBevares": "skalBevares"}');

UPDATE hendelse
SET data = jsonb_set(data - 'opprettetAv', '{utførtAv}', data->'opprettetAv')
WHERE type = 'OPPRETTET_TILBAKEKREVINGSBEHANDLING' AND data ? 'opprettetAv';

SELECT * FROM hendelse;
*/
