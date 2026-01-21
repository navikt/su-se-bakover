/*
Sletter aller linjer hvor sak ble opphørt i en tidligere måned.

Spørring for å finne sak_id og maaned:
SELECT sak_id, maaned
FROM (
         SELECT sak_id,
                maaned,
                vedtaksdato,
                opphorsdato,
                COUNT(*) OVER (
                    PARTITION BY sak_id, vedtaksdato, opphorsdato
                    ) AS cnt,
                ROW_NUMBER() OVER (
                    PARTITION BY sak_id, vedtaksdato, opphorsdato
                    ORDER BY maaned ASC
                    ) AS rn
         FROM stoenad_maaned_statistikk
         WHERE opphorsdato IS NOT NULL
     ) t
WHERE cnt > 1
  AND rn > 1;
*/
delete from stoenad_maaned_statistikk
where sak_id = '050edc83-60a7-4c9f-a145-25e5cd2b7511' AND maaned = '2025-10-01'
OR sak_id = '050edc83-60a7-4c9f-a145-25e5cd2b7511' AND maaned = '2025-11-01'
OR sak_id = '1c820669-9aa2-4b43-91a6-597184f807e6' AND maaned = '2025-11-01'
OR sak_id = '298b4e79-edbf-43f2-aab0-4db242b98d8f' AND maaned = '2025-11-01'
OR sak_id = '3ef5aa79-265f-45a4-9530-fb8b60586adc' AND maaned = '2025-10-01'
OR sak_id = '3f46be64-1044-43d3-aa2d-e0978bcd7bb3' AND maaned = '2025-10-01'
OR sak_id = '3f46be64-1044-43d3-aa2d-e0978bcd7bb3' AND maaned = '2025-11-01'
OR sak_id = '40f4b05b-9525-4181-a867-925c934b037c' AND maaned = '2025-11-01'
OR sak_id = '4c85b1d4-cfed-46ca-a50a-664a72d29edf' AND maaned = '2025-10-01'
OR sak_id = '4c85b1d4-cfed-46ca-a50a-664a72d29edf' AND maaned = '2025-11-01'
OR sak_id = '513f7462-0bda-408a-8547-8bd2fee99905' AND maaned = '2025-10-01'
OR sak_id = '513f7462-0bda-408a-8547-8bd2fee99905' AND maaned = '2025-11-01'
OR sak_id = '5d54b4d7-ec7c-4545-b9fd-e4fec497938c' AND maaned = '2025-10-01'
OR sak_id = '5d54b4d7-ec7c-4545-b9fd-e4fec497938c' AND maaned = '2025-11-01'
OR sak_id = '61691091-0bef-446b-9a56-a7759ce52ed7' AND maaned = '2025-10-01'
OR sak_id = '61691091-0bef-446b-9a56-a7759ce52ed7' AND maaned = '2025-11-01'
OR sak_id = '65fa82fc-1f1b-431d-aba6-bad8e0696531' AND maaned = '2025-11-01'
OR sak_id = '8e4a7c14-f9b2-4bbb-bd7a-834fcaf3743b' AND maaned = '2025-11-01'
OR sak_id = 'a1936b8e-3794-464e-96c8-bf9138229c83' AND maaned = '2025-10-01'
OR sak_id = 'a30b2131-23b2-4135-bd9f-7cca4230234b' AND maaned = '2025-11-01'
OR sak_id = 'b11f9f9f-6f24-462b-97cf-4754306464cc' AND maaned = '2025-10-01'
OR sak_id = 'b11f9f9f-6f24-462b-97cf-4754306464cc' AND maaned = '2025-11-01'
OR sak_id = 'b315a572-3593-433f-acd2-06bfa364e486' AND maaned = '2025-10-01'
OR sak_id = 'b315a572-3593-433f-acd2-06bfa364e486' AND maaned = '2025-11-01'
OR sak_id = 'c3a67187-330c-43dc-b3e5-3b03dabe7fde' AND maaned = '2025-11-01'
OR sak_id = 'c67f7a92-9883-4b03-a3a7-ac88f709ae52' AND maaned = '2025-10-01'
OR sak_id = 'c67f7a92-9883-4b03-a3a7-ac88f709ae52' AND maaned = '2025-11-01'
OR sak_id = 'db3548be-e2de-484a-8bad-c95f68afeed4' AND maaned = '2025-10-01'
OR sak_id = 'db3548be-e2de-484a-8bad-c95f68afeed4' AND maaned = '2025-11-01'
OR sak_id = 'e6431ec6-6898-45e5-bc0c-667a80324671' AND maaned = '2025-10-01'
OR sak_id = 'e72eb007-12d8-453f-add1-9f0b44dac3fd' AND maaned = '2025-10-01'
OR sak_id = 'e72eb007-12d8-453f-add1-9f0b44dac3fd' AND maaned = '2025-11-01'
OR sak_id = 'eb22a02f-1fd8-4dfa-bff7-78980deeeeb4' AND maaned = '2025-10-01'
OR sak_id = 'eb22a02f-1fd8-4dfa-bff7-78980deeeeb4' AND maaned = '2025-11-01'
OR sak_id = 'ec2bbe83-808b-40ab-ba49-7b2d99498b61' AND maaned = '2025-10-01'
OR sak_id = 'ec2bbe83-808b-40ab-ba49-7b2d99498b61' AND maaned = '2025-11-01'
OR sak_id = 'ecc0b6d0-2744-435d-bd28-fafe08c530e3' AND maaned = '2025-10-01'
OR sak_id = 'ecc0b6d0-2744-435d-bd28-fafe08c530e3' AND maaned = '2025-11-01'
OR sak_id = 'f0318bc7-3420-45b8-9652-726923a100c2' AND maaned = '2025-10-01'
OR sak_id = 'f0318bc7-3420-45b8-9652-726923a100c2' AND maaned = '2025-11-01'
OR sak_id = 'f31d5b2f-1889-4052-9371-01c883ccec6a' AND maaned = '2025-11-01'
OR sak_id = 'f412cc14-f5b1-4615-a5a8-cda9963f3e97' AND maaned = '2025-11-01'
OR sak_id = 'fad78511-0266-4f8f-beaa-49a8c7bf0f58' AND maaned = '2025-11-01';