-- Kravgrunnlaget kan ikke mappes uten TREK-støtte og er avløst av et nyere kravgrunnlag.
-- Kvitteringen gjør at konsumenten ikke prosesserer råhendelsen. Det opprettes derfor ingen
-- KNYTTET_KRAVGRUNNLAG_TIL_SAK-hendelse, og råhendelsen kan ikke brukes i saksbehandlingen.
-- se https://nav-it.slack.com/archives/CKZADNFBP/p1790171158544949
INSERT INTO hendelse_konsument (id, hendelseId, konsumentId)
SELECT
    gen_random_uuid(),
    hendelseId,
    'KnyttKravgrunnlagTilSakOgUtbetaling'
FROM hendelse
WHERE hendelseId = 'c445e80d-00e9-49fd-81b0-4288dd9e2ef1'
ON CONFLICT (hendelseId, konsumentId) DO NOTHING;
