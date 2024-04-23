UPDATE regulering
SET arsakForManuell = (
    SELECT JSONB_AGG(
                   CASE
                       WHEN value::text = 'ForventetInntektErStørreEnn0' THEN JSONB_BUILD_OBJECT('type', value::text, 'begrunnelse', NULL)
                       WHEN value::text = 'YtelseErMidlertidigStanset' THEN JSONB_BUILD_OBJECT('type', value::text, 'begrunnelse', NULL)
                       ELSE JSONB_BUILD_OBJECT('type', value::text)
                       END
           )
    FROM JSONB_ARRAY_ELEMENTS_TEXT(arsakForManuell) AS value
);