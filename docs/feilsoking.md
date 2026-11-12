# Feilsøking

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
