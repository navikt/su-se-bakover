Siden dette er første application/infrastructure-inndelingen i prosjektet hører det til med en liten `readme`.
I ddd-verden deles det ofte inn i 4 lag
1. presentation - typisk innkommende lag, f.eks. http-laget (rest/soap/graphql).
2. application - bindeledd mellom presentation, model og infrastructure.
3. model - businesslogikk (som ikke har noen direkte tilknytning til infrastructure og med færrest mulig dependencies)
4. infrastructure - typisk utgående lag, f.eks. http-kall til andre tjenester, publisering på meldingskø, persistence

En enklere utgave er å kun dele opp i:
1. application (som implisitt inneholder domain).
2. infrastructure (som implisitt inneholder presentation).

Generelle regler:
- application kan ikke ha referanser til infrastructure.
- application har interfaces der den må gjøre kall til infrastructure-laget.
- infrastructure har referanser til application.
- infrastructure har ansvaret for setup.

Foreslår å prøve sistenevnte først og se om det fungerer for oss.
