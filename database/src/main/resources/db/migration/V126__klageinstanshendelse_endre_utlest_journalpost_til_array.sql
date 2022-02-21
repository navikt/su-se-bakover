alter table klageinstansvedtak rename to klageinstanshendelse;

alter table klageinstanshendelse
    alter utlest_journalpostid type text[] using case when utlest_journalpostid is null then null else array[utlest_journalpostid] end;

-- Klageinstans/Kabal skal fase ut denne topicen, så vi ønsker å kunne differensiere hvilket topic vi fikk hendelsen fra.
update klageinstanshendelse SET metadata = metadata || '{"topic":"klage.vedtak-fattet.v1"}'::jsonb