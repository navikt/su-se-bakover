ALTER TABLE
    dokument_skatt
    ADD COLUMN IF NOT EXISTS
        skattedataHentet timestamp with time zone not null default to_timestamp('2023-05-26 13:10:21.000000 +00:00',
                                                                                'YYYY-MM-DD HH24:MI:SS Europe/Oslo');