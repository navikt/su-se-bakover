# Historikk

Utdatert innhold fra README. Det beskriver on-prem-oppsett og tidligere migreringer, og
beholdes bare som referanse. Ikke bruk det som oppskrift for nye oppgaver uten å
kontrollere at det fortsatt gjelder.

## Hvordan kunne koble til Test/prod baser fra lokal maskin

1. `sudo nano /etc/hosts`
2. Legg inn følgende:
    ```
    # NAV POSTGRES PREPROD URL
    10.53.17.46 b27dbvl030.preprod.local
    # NAV POSTGRES PROD URL
    10.53.15.186 A01DBVL036.adeo.no
    ```
3. lagre

## Migrere data fra/til postgres on-prem (utdatert)

Se også https://github.com/navikt/utvikling/blob/main/docs/teknisk/PostgreSQL.md

1. Lag en PR tilsvarende denne: https://github.com/navikt/database-iac/pull/511
3. Sjekk at den nye databasen er satt opp og du kan logge inn med vault bruker. Påse at alle extensions er lagt til.
   E.g. `CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;`. Hvis ikke hør i #postgres i slack.
4. Merk at vi ønsker plain sql siden vi skal gjøre endringer:
   `pg_dump -U <vault-db-username> -W -h b27dbvl030.preprod.local -p 5432 supstonad-db-15-dev > preprod.dump`
5. Vi må endre db og roles: `sed -i '.bak' 's/supstonad-db-15-dev/<ny-db>/g' preprod.dump`
6. Fjern extensions-linjene fra dumpen dersom de allerede er utført.
7. Legg til `SET ROLE '<db>-admin';` øverst i dumpen.
8. Lag en PR i su-se-bakover med endringene for først preprod og senere en pr for prod.
9. `kubectl scale deployment su-se-bakover --replicas=0` eller alternivt slett deploymenten.
10. `kubectl delete cronjob -l app=su-datapakke-soknad`
11. Kopier skjema+data til ny base:
    `PGPASSWORD='<vault-db-passord>' psql -h <ny-db-host>> -p 5432 -U <vault-db-username> -d <ny-db> -f preprod.dump`
12. Hvis det går bra, merge PRen og deploy. Replicas blir da resatt basert på nais.yml.
