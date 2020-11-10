UPDATE
    søknad
SET
    søknadinnhold = jsonb_set(
        søknadinnhold,
        '{forNav, type}',
        to_jsonb('DigitalSøknad'::text)
    );
