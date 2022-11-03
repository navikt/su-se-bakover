Siden dette er første application/infrastructure-inndelingen i prosjektet hører det til med en liten `readme`.
I ddd-verden deles det ofte inn i 4 lag
1. `Presentation` (tidligere web) - typisk innkommende lag, f.eks. http-laget (rest/soap/graphql), og mer spesifikt konsumering av Kafka og IBM MQ. Inneholder også builders for å sy sammen domene-abstraksjonene og infrastruktur-laget.
2. `Application` (tidligere service) - bindeledd mellom `Presentation` og `Domain`. Håndterer ofte side-effekter. Tidligste nivået man starter en transaksjon.
3. `Domain` - businesslogikken bor her. Kun avhengigheter til andre `Domain`. Inneholder domene-abstraksjonen  (interface) mot infrastruktur som persistering og klienter.
4. `Infrastructure` (tidligere database og client) - typisk utgående lag, f.eks. http-kall til andre tjenester, publisering på meldingskø, persistence. Kun avhengigheter mot `Application` og `Domain`.

Generelle regler:
- `Presentation` har i hovedsak avhengigheter til `Application` og `Infrastructure`. Tidvis avhengighet til `Domain`, men ideélt sett ikke.
- `Application` har avhengigheter til `Domain` og andre `Application`.
- `Domain` har kun avhengigheter til andre `Domain` (f.eks. common domain og ting som inngår i dette aggregatet).
- `Infrastructure` har avhengigheter til `Application` og `Domain`
