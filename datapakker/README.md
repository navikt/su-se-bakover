# Datapakker
En naisjobb (cronjobb) vil starte en ny pod hver gang den eksekverer.

## kubernetes kommandoer
Slette alle cronjobber, jobber og podder med app=navn.

Våre app navn:
```
su-datapakke-soknad
```

```
kubectl delete cronjob -l app=navn --namespace=supstonad
kubectl delete job -l app=navn --namespace=supstonad
kubectl delete pod -l app=navn --namespace=supstonad
```

Start en manuell cronjob (husk å slett dersom den feiler):
```
kubectl create job --namespace=supstonad --from=cronjobs/su-datapakke-soknad manuell-test-custom-name-here
```


## How-to
### Lage en ny datapakke


#### prerequisites 
1. legg inn `Google Cloud Platform` fra `myapps.microsoft.com`
2. En i teamet må inn i BigQuery og gi deg tilgang til prosjektene.
    - Klikk på selecten øverst til venste
    - En Modal popper opp, klikk på mappen med et tannhjul ved siden av `new project`
    - finn fram `supstonad-dev` & `supstonad-prod`
    - klikk på knappen `add principal`
    - Legg in nav-mail som principal, og disse rollene (trenger kanskje ikke alle - men er disse vi har på dette tidspunktet); 
      - `BigQuery Admin`
      - `BigQuery Data Editor`
      - `BigQuery Data Owner`
      - `BigQuery Data Viewer`
      - `Service Account Admin`
      - `Service Account Key Admin`
      - `Service Usage Admin`
      - `naisdeveloper` (for supstonad-prod)

#### Oppsett
En datapakke har 3 essensielle deler - en database connection, og config for GCP/BigQuery, og en naisjob.
Hovedpoenget med en datapakke er å hente fram data, og lagre det i en BigQuery tabell, for å muligens visualisere det i Metabase.

1. Lag en connection mot databasen
2. kjør ønsket spørring mot databasen
3. konfigurer GCP/BigQuery
4. Lag en nais.yml & dockerfile for å kjøre jobben i nais


#### GCP
1. Gå til `console.cloud.google.com`
2. Velg `BigQuery` fra Quick access
3. Velg `supstonad-dev` eller `supstonad-prod` fra selecten øverst til venstre
4. klikk 3-dotter på ressursen i venstre menyen -> `create dataset`
   - `Dataset ID` skal være skal speile datasett navnet du satt i BigQuery configen i koden
   - location settes til `Region` & `europe-north1`
   - Klikk `create dataset`
5. Klikk 3-dotter på datasettet du nettopp lagde -> `create table`
   - create table from `empty table`
   - `table` skal speile tabell navnet du satt i BigQuery configen i koden
   - Legg til ønskelig schema
     - eksempel `id` `string` `required`
   - klikk `create table`
6. Det samme må nå gjøres for det andre miljøet


#### Metabase
TODO