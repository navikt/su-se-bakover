update revurdering
set forhåndsvarsel = to_jsonb('{"type": "IngenForhåndsvarsel"}'::json)
where forhåndsvarsel is null
  and revurderingstype in ('TIL_ATTESTERING_INNVILGET',
                           'TIL_ATTESTERING_OPPHØRT',
                           'TIL_ATTESTERING_INGEN_ENDRING',
                           'IVERKSATT_INNVILGET',
                           'IVERKSATT_OPPHØRT',
                           'IVERKSATT_INGEN_ENDRING',
                           'UNDERKJENT_INNVILGET',
                           'UNDERKJENT_OPPHØRT',
                           'UNDERKJENT_INGEN_ENDRING');