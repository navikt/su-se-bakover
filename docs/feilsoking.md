# Feilsøking

## Debugging i Nais med distroless

App-imaget har ikke shell. Bruk `kubectl debug` til å starte en midlertidig
debug-container i samme pod, uten å restarte appen eller endre app-imaget.
Eksemplene krever ikke endringer i Dockerfile eller Javas oppstartsflagg.
Dette krever tilgang til clusteret og rettighet til å legge til slike containere.
Se [Kubernetes-oppsettet](drift.md#kubernetes) hvis du mangler tilkobling.

Velg `dev-gcp` for utvikling eller `prod-gcp` for produksjon. Eksemplet velger
`dev-gcp`. Kontroller aktiv kontekst og namespace etter miljøbyttet:

```bash
kubectl config use-context dev-gcp
kubectl config current-context
kubectl config view --minify -o jsonpath='{.contexts[0].context.namespace}{"\n"}'
```

Namespace for denne appen er alltid `supstonad`. Tomt namespace betyr at
Kubernetes bruker `default`. Hvis namespace ikke er `supstonad`, sett det og
kontroller på nytt:

```bash
kubectl config set-context --current --namespace=supstonad
kubectl config view --minify -o jsonpath='{.contexts[0].context.namespace}{"\n"}'
```

Kommandoene under bruker aktiv kontekst og standard-namespace. Kontroller dem
på nytt hvis du bytter miljø underveis. Når du skal teste et nytt image, vent
til deployment til valgt miljø er ferdig og den nye app-poden er `Ready`.
Finn deretter den nye poden:

```bash
kubectl get pods -l app=su-se-bakover
```

Sett `POD` til podnavnet fra listen, og finn navnene på containerne:

```bash
POD=sett-inn-podnavn
kubectl get pod "$POD" \
  -o jsonpath='{.spec.containers[*].name}{"\n"}'
```

Sett `APP_CONTAINER` til app-containeren, ikke en sidecar. Start deretter
debug-containeren:

```bash
APP_CONTAINER=sett-inn-appcontainer
kubectl debug -it "$POD" \
  --target="$APP_CONTAINER" \
  --image=europe-north1-docker.pkg.dev/nais-io/nais/images/debug:latest \
  --profile=restricted
```

### Hva kan undersøkes?

| Område | Muligheter og begrensninger |
|--------|----------------------------|
| Logger | Les ordinær app-logg med `kubectl logs`, også fra forrige kjøring etter en restart. Ingen debug-container trengs. |
| Filer | Debug-containerens filsystem er separat fra appens. Volum-montering er blokkert i clusteret. |
| Nettverk | Test navneoppslag og forbindelser fra samme podnettverk. |
| Java-krasj | Se avslutningsårsak, avslutningskode og loggen fra forrige kjøring. |
| Minne og tråder | Vi undersøker hvordan dette kan gjøres i Nais. |

### Logger og krasjstatus

Kjør fra den lokale terminalen, ikke inne i debug-containeren. Kommandoene gjelder
ordinær app-logg, ikke sikkerlogger:

```sh
kubectl logs "$POD" -c "$APP_CONTAINER" --tail=100
```

Hvis app-containeren har restartet, hent loggen fra forrige kjøring:

```sh
kubectl logs "$POD" -c "$APP_CONTAINER" --previous --tail=100
```

Se antall restarter og siste avslutningsårsak:

```sh
kubectl get pod "$POD" \
  -o jsonpath='{range .status.containerStatuses[*]}{.name}{": restarter="}{.restartCount}{", sist="}{.lastState.terminated.reason}{", exit="}{.lastState.terminated.exitCode}{"\n"}{end}'
```

Ved `OOMKilled` har containeren blitt drept på grunn av minnebruk. Java rekker
ikke nødvendigvis å skrive en minnedump før dette skjer. Dette er noe annet enn
at Java selv rapporterer `OutOfMemoryError`. En avslutningskode alene fastslår
ikke årsaken.

### Filer og nettverk

Debug-containeren har sitt eget filsystem, inkludert `/tmp`.

`jcmd` og `jmap` trenger tilgang til tilkoblingsfilene Java bruker i appens
`/tmp` for å sende diagnosekommandoer til den kjørende Java-prosessen.
Ved å montere appens eksisterende volum `writable-tmp` som `/tmp` også i
debug-containeren ville begge sett de samme filene. `writable-tmp` er navnet
på volumet i Nais-oppsettet, ikke et Java-flagg.

I første forsøk i `dev-gcp` var `/tmp` i debug-containeren eid av root uten
skrivetilgang for debug-brukeren. Tilgang via `/proc/1/root/tmp` ble også
avvist. Java-verktøyene fikk derfor ikke opprettet filen for tilkoblingen
og feilet med `Permission denied`. Verktøyene var installert; problemet var
filtilgang.

Neste forsøk skulle starte en ny debug-container med delt `writable-tmp`.
Clusterets sikkerhetsregel `deny-ephemeral-volumemounts` avviste dette fordi
volumer kan inneholde hemmeligheter. Den nye debug-containeren ble derfor
ikke opprettet. Vi undersøker en støttet fremgangsmåte for minne- og
trådanalyse uten å omgå denne regelen.

Kjør inne i debug-containeren. Med `curl` tilgjengelig i debug-imaget kan du
sjekke appens helse fra samme pod:

```sh
curl --fail --silent --show-error --max-time 5 http://127.0.0.1:8080/isalive
curl --fail --silent --show-error --max-time 5 http://127.0.0.1:8080/isready
```

Debug-containeren deler podens nettverk, men arver ikke automatisk appens
miljøvariabler, sertifikater eller autentisering. Et vellykket nettverkskall
bekrefter derfor ikke at Java-klientens konfigurasjon er riktig.

### Eksempel: prosessorkravene til Java4Shell

En debug-container kan brukes til å undersøke prosessorfunksjonene noden gjør
tilgjengelige. Java4Shell krever x86-64 og alle fire funksjonene nedenfor.
Kjør inne i debug-containeren:

```sh
uname -m
grep -m1 '^flags' /proc/cpuinfo | tr ' ' '\n' \
  | grep -E '^(avx512f|avx512bw|avx512vbmi|bmi2)$'
```

Eksempel på resultat:

```text
x86_64
bmi2
```

Denne noden oppfyller ikke prosessorkravene: Den viser bare `bmi2`, ikke de
nødvendige `avx512f`, `avx512bw` og `avx512vbmi`. Resultatet gjelder noden
poden kjører på nå, ikke automatisk andre noder eller produksjon. Poden kan
også flyttes til en annen node senere.

Java-flagget `-XX:-UseBASE64Intrinsics` endrer ikke resultatet i `/proc/cpuinfo`.
Det slår av Javas optimaliserte Base64-implementasjon, ikke prosessorfunksjonene.
Behold flagget til Java-builden er bekreftet rettet. At alle fire
prosessorfunksjonene finnes, er heller ikke alene bevis på at appen er utnyttbar.

Avslutt debug-shellet med `exit` når du er ferdig. Debug-containeren stopper,
mens appen fortsetter å kjøre. Containeroppføringen blir stående til poden
erstattes.

Kilder:

- https://docs.nais.io/workloads/how-to/debugging/#kubectl-debug
- https://kubernetes.io/docs/tasks/debug/debug-application/debug-running-pod/#ephemeral-container
- https://calif.io/java4shell

## Colima og Testcontainers på macOS

Ved `no match for platform in manifest`, sjekk serveren og miljøvariablene:

```sh
colima version
colima status
printenv DOCKER_HOST TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE api.version
docker version --format 'Engine={{.Server.Version}} API maks={{.Server.APIVersion}} API min={{.Server.MinAPIVersion}} Arkitektur={{.Server.Arch}}'
docker info --format 'Driver={{.Driver}} Status={{json .DriverStatus}}'
```

### Oppdatere Colima og opprette en ny profil

En Colima-profil har en egen virtuell Linux-maskin med Docker. Opprett en ny
profil for å unngå problemer med et gammelt operativsystem og utgåtte
pakkearkiver, for eksempel `mantic Release`. Profilen heter `pdf-test` i eksemplet:

```sh
brew upgrade colima # Oppdaterer Colima-programmet på Mac-en.
colima start pdf-test --runtime docker # Oppretter profilen pdf-test ved første kjøring og starter den med Docker.
docker --context colima-pdf-test version # Viser klient- og serverversjonen for profilen pdf-test.
```

Den gamle profilen beholdes. Containere, images og databasevolumer flyttes ikke
til den nye profilen. Ikke slett den gamle profilen eller volumene.

Erstatt gamle Docker-eksporter i `~/.zshrc` med:

```sh
export DOCKER_HOST="unix://${HOME}/.colima/pdf-test/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Lagre filen og last inn verdiene:

```sh
source ~/.zshrc
printenv DOCKER_HOST TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE
docker version
```

`DOCKER_HOST` velger serveren for Docker-klienten og Testcontainers, men ikke
hvilken profil Colimas administrasjonskommandoer bruker. Socket-overstyringen
gjelder serversiden og skal fortsatt være `/var/run/docker.sock`.

Kjør PDF-testen eksplisitt mot den nye profilen uten API-overstyring:

```sh
env -u 'api.version' \
  DOCKER_HOST="unix://$HOME/.colima/pdf-test/docker.sock" \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  ./gradlew :service:test --tests '*PdfByggerMigreringTest' --rerun --info
```

Testloggen skal vise `pdf-test/docker.sock` og den nye serverversjonen.
Nedlasting eller vellykket containeroppstart alene betyr ikke at testen er grønn;
kontroller sluttresultatet.

### Kort om årsaken

Testcontainers `2.0.5` prøver API `1.44` som standard og faller tilbake til `1.32`
ved feil. Gammel Docker `24.0.9` støtter maks `1.43`, mens plattformparameteren
krever minst `1.41`. I tillegg har Docker 24 en containerd-begrensning som kan
avvise AMD64-images på ARM64 uten eksplisitt plattform. Den er rettet i
Docker `25.0.1`. Om gammel profil brukte denne lagringsmotoren, er ikke bekreftet;
`io.containerd.snapshotter.v1` i `DriverStatus` viser containerd-lagring.
`withPlatform` velger image-plattform, men aktiverer ikke emulering.

Kilder:

- https://colima.run/docs/faq/#how-can-the-vm-and-tools-be-updated
- https://github.com/testcontainers/testcontainers-java/blob/2.0.5/core/src/main/java/org/testcontainers/dockerclient/DockerClientProviderStrategy.java
- https://github.com/moby/moby/commit/fb19f1fc20231e7751f315f6a23558d6a1af0760

## Lokal/embedded postgres

Dersom man får feilene

- `running bootstrap script ... 2022-08-30 16:10:20.342 CEST [53606] FATAL:  could not create shared memory segment: Cannot allocate memory`
- `java.lang.IllegalStateException: Process [/var/folders/l_/c1b_7t2n39j48k7sbpp54zy00000gn/T/embedded-pg/PG-8eddc1e460ca1c5597350c162933683c/bin/initdb, -A, trust, -U, postgres, -D, /var/folders/l_/c1b_7t2n39j48k7sbpp54zy00000gn/T/epg3835758492450081687, -E, UTF-8] failed`

kan du prøve disse 2 mulighetene:

1. Hvis du ikke har; legg inn
    * `export LC_CTYPE="en_US.UTF-8"`
    * `export LC_ALL="en_US.UTF-8"`

   i din terminal profil (f.eks .zshrc). Dersom det ikke fikser problemet, kan du også prøve punktet over.

2. øke shared memory:
    * `sudo sysctl kern.sysv.shmmax=104857600` eller en annen ønsket verdi, for eksempel `524288000`
    * `sudo sysctl kern.sysv.shmall=2560` eller en annen ønsket verdi, for eksempel `65536`
