# Database

Oppstart og nullstilling av lokal database er beskrevet i [Lokal utvikling](lokal-utvikling.md#database-postgres).

## Koble til database i preprod/prod

1. `nais auth login --nais`
2. Velg ønsket context.
   3. Dev (med kubectx): `kubectx dev-gcp`. Dev (uten kubectx): `kubectl config use-context dev-gcp`
    - I noen tilfeller må default environment også settes: `nais defaults set environment dev-gcp`
   4. Prod (med kubectx): `kubectx prod-gcp`. Prod (uten kubectx): `kubectl config use-context prod-gcp`
3. `nais postgres prepare su-se-bakover` (kun første gang)
4. `nais postgres grant su-se-bakover` (kun første gang)
5. `nais postgres proxy su-se-bakover --reason`

## Kopiere data fra dev til lokal database (alternativt se delen "import" lenger ned)
1. Kjør `nais auth login --nais` 
2.  `kubectx dev-gcp`
3.  `nais postgres proxy su-se-bakover --reason kopiererdevdata`
4. `pg_dump postgresql://brukernavn%40nav.no@localhost:5432/supstonad > dev_dump.sql --data-only`
5. `cat dev_dump.sql | docker exec -i su-se-bakover-postgres-1 \psql -U user -d supstonad-db-local`
6. Logg ut av dev miljø og refresh lokal databse

### Hvis du har lokal data må du slette det før du kopiererer dev data. Dette kan gjøre ved å kjøre denne:
````
DROP SCHEMA public CASCADE;

CREATE SCHEMA public;
````

## Backup og import av database

For backup av testmiljøets database, må du hente brukernavn og password (admin - se Koble til database i preprod/prod)
fra vault.

Backup

1. For å ta en kopi av databasen kan du bruke følgende script i
   terminalen `pg_dump -U username -W -h remote_hostname -p remote_port -F c database_name > filnavn.dump`
    - eksempel for lokalt `pg_dump -U user -h localhost -p 5432 -F c supstonad-db-local > filnavn.dump`
        - password `pwd`
    - eksempel for
      test `pg_dump -U brukernavnFraVault -h b27dbvl030.preprod.local -p 5432 -F c supstonad-db-15-dev > filnavn.dump`

Import

1. For å legge inn dataen i basen kan du kjøre
   følgende `pg_restore -h remote_host -p remote_port -U brukernavn -d database_name filnavn.dump`
    - eksempel for lokalt `pg_restore -c --if-exists -h localhost -p 5432 -U user -d supstonad-db-local filnavn.dump`
        - password er `pwd`
        - Terminal vil kanskje gi deg en del 'errors' pga roller etc. Disse kan du se bort ifra
        - Hvis du samtidig inspecter filen (f.eks nano) vil du kanskje se en del encoding issues. Disse var heller ikke
          et problem ved import

## Select og se PDF i database

1. `SELECT encode(generertdokument, 'base64') FROM dokument where revurderingId = '<REPLACE_ME>';`
2. `base64 --decode -i input.base64 -o output.pdf`
