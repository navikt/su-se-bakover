update dokument d
set generertdokumentjson = (d.generertdokumentjson #>> '{}')::jsonb
where "left"(d.generertdokumentjson::text, 1) = '"';
