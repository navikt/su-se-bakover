# Drift

## Kubernetes

Et par ting du må gjøre hvis du har lyst til å gjøre noe snacks i kubernetes.

1. Du må ha nais-cli - https://docs.nais.io/cli/
2. Du må også ha gcloud-cli (kanskje?) - https://cloud.google.com/sdk/docs/install
3. log inn i gcloud - `gcloud auth login --update-adc`
4. Kjør `nais kubeconfig` - Dette vil hente ned alle Clusterene.
    1. merk: Kjør `nais kubeconfig -io` for å hente on-prem clusters også (dev-gcp/prod-gcp)
5. Sett context du hr lyst til å bruke
    1. for eksempel: `kubectl config use-context dev-gcp`
    2. Hvis du må sette namespace i tillegg: `kubectl config set-context --current --namespace=supstonad`
6. Nå skal du kunne kjøre `kubectl get pods` og få listet alle podene våre

Tilkobling til databasen i preprod/prod er beskrevet i [Database](database.md#koble-til-database-i-preprodprod).

## Metrics

Vi eksponerer Prometheus-kompatible metrikker på `/metrics` via Micrometer.
NAIS scraper disse, og de er tilgjengelige i Grafana via Mimir.
Se https://doc.nais.io/observability/metrics.

## Alerts

`alerts.yml` deployes automatisk vha. `.github/workflows/alerts-deploy-gcp.yml` og
benytter [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/basics/) for å sette opp
alerts basert på metrikker.
Dette skal fungere videre med Mimir.
Se https://doc.nais.io/observability/alerts.

### Utforske metrikker

Bruk Grafana Explore og datakilden `Metrics`.
For nye dashboards bør `Metrics` brukes.
Ved spørringer på tvers av clustere brukes labelen `k8s_cluster_name`.

### Se alerts

Bruk NAIS Console for oversikt over alerts.

### Kubectl commands

* View alerts: `kubectl --namespace=supstonad get alerts`
* Describe alert: `kubectl --namespace=supstonad describe alert su-se-bakover`
* Delete alert: `kubectl --namespace=supstonad delete alert su-se-bakover`
* Deploy new alert: Just run the `alerts-deploy.yml` GitHub actions workflow

## IBM MQ (oppdrag/utbetaling/simulering)

Dersom man ønsker å inspisere meldingene i preprod-miljøet:

* Via VmWare (utvikler image): http://a34drvw006.devillo.no:8000/ navn på hostname/lø ligger i `.nais/dev.yaml`.

I preprod: Dersom man ønsker å slette/endre/legge til meldinger eller flytte fra backoff til hovedkø, kan man
bruke https://github.com/jmstoolbox/jmstoolbox

* Finn hostname/kø i `.nais/dev.yaml`.
* Ping hostname for å få IP `10.53.17.118`
* Finn username/password i vault: https://vault.adeo.no/ui/vault/secrets/serviceuser/show/dev/srvsupstonad
* Legg inn channel: `Q2_SU_SE_BAKOVER`
* Legg inn queueManager `MQLS02`
* Finn en passende Ibm mq jar: https://github.com/jmstoolbox/jmstoolbox/wiki/2.1-Setup-for-IBM-MQ
