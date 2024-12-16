# Datapakker
En naisjobb (cronjobb) vil starte en ny pod hver gang den eksekverer.

## kubernetes kommandoer
Slette alle cronjobber, jobber og podder med app=navn.

Våre app navn:
```
su-datapakke-soknad
su-datapakke-fritekstAvslag
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
   - kolonne navn må ikke inneholde æøå  
4. Lag en nais.yml & dockerfile for å kjøre jobben i nais
5. Lag en PR i `github.com/navikt/vault-iac` for at appen skal ha tilgang til vault secrets
   - Se for eksempel på `github.com/navikt/vault-iac/tree/master/terraform/teams/supstonad/apps`
   - En annen i teamet kan approve PR'en
6. Husk å oppdatere '.github/workflows/datapakker.yml'. Per nå dupliserer vi litt mye, så du kan gjerne gjøre noe smart istedenfor :)
7. Kopier build.gradle.kts i sin helhet og endre på navn og dependencies. jar-tasken er viktig, da den bygger en jar-fil som støttes av baseimages.

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


### Visualisering av data fra BigQuery

#### NADA
Før du kan visualisere dataen ved bruk av Metabase, må vi legge til datapakken vår i NADA.
1. Gå til `data.ansatt.nav.no` (dev - `data.intern.dev.nav.no`)
2. Finn fram til supstonad - eksempel på hvordan du kan finne fram
   - klikk på `Utforsk områder`
   - velg `Produkområde Pensjon` i selected øverst til venstre
   - velg `SU` fra menyen til venstre
   - Klikk på `Produkter` fanen øverst i midten
   - Klikk på `supstonad`
   - Gjerne utforsk hva vi allerede har 
3. Ved menyen til venstre, klikk `Legg til datasett`
4. Fyll inn informasjon som ønsket
   - `Velg tabell eller view` lister opp bare prod-basen i BigQuery. Dersom du bare har lagt til dev på dette tidspunktet, må du gå tilbake, og legge til prod.
   - Klikk `Lagre`
5. Legg til tilganger hvis den ikke er åpen for alle. 
6. sjekk at datasettet ser riktig ut
7. Under overskiften, har du forskjellige links, trykk på `Legg til metabase` hvis du har ønske om å visualisere datasettet i Metabase

Mer info om NADA, etc kan finnes på `docs.knada.io`


#### Metabase
Hvis du vil visualisere dataen fra BigQuery, kan dette gjøres i Metabase. Datasettet må ha vært lagt til i NADA. Hvis ikke dette er gjort, må du gjøre det først. 
Tilgang til metabase kommer når du har lagt til GCP i `myapps.microsoft.com` (???)
1. gå og logg inn til `metabase.ansatt.nav.no` (dev - `metabase.intern.dev.nav.no`)
2. Åpne sidepanelet til venstre: PO Pensjon -> Team Supplerende Stønad
3. Klikk på `new`
   - Her er det litt valg muligheter.
   - Dersom du er interessert i å lage en basic spørring mot datasettet vi la inn i NADA, velger du `Question`
   - Dersom du vil gruppere sammen questions, kan du velge `Dashboard`
     - Ved valg av `Question`
       1. Velg `Raw Data`
       2. skriv inn navnet på tabellen som du lagde i GCP  
       3. Visaliser dataen som øsnkelig
       4. Klikk `Visualize`

Litt mer info om metabase kan finnes på `docs.knada.io/analyse/metabase`