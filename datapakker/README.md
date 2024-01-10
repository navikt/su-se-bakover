# Datapakker
En naisjobb (cronjobb) vil starte en ny pod hver gang den eksekverer.

## kubernetes kommandoer
Slette alle cronjobber, jobber og podder med app=navn.

Våre app navn
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
