# Autentisering lokalt

su-se-framover tar seg av autentisering (backend for frontend (BFF)) og kaller su-se-bakover med on-behalf-of tokens (
per bruker).

Lokalt kjøres det opp en mock oauth2-server på http://localhost:4321 .
Se https://github.com/navikt/su-se-framover#mock-oauth-server for mer informasjon.

## Uten innlogging i Azure (default)

Applikasjonen vil selv generere gyldige jwt-tokens for inkommende kall. Ved behov kan innholdet i disse konfigureres
i `JwtStub.kt`.

Merk at tokens som genereres automatisk av appen kun vil være gyldige for den aktuelle instansen som genererte den.
For å unngå at man må starte helt fra "login" når applikasjonen restartes, kan man heller slette gamle `access_token`fra
browser,
dette fører til at det gjenværende `refresh_token` benyttes til å generere nye.

## Bytte saksbehandlerident (attestering)

Lokalt bruker appen alltid identen `Z9999991`, uansett hvilket token som kommer inn.
Attestanten må være en annen enn saksbehandleren. Du må derfor bytte ident for å
attestere en sak du selv har sendt til attestering.

1. Behandle saken og send den til attestering.
2. Endre `Z9999991` til en annen ident, for eksempel `Z9999992`, i `getNAVidentFromJwt` i
   [`Extensions.kt`](../common/infrastructure/src/main/kotlin/no/nav/su/se/bakover/common/infrastructure/web/Extensions.kt):

   ```kotlin
   fun getNAVidentFromJwt(applicationConfig: ApplicationConfig, principal: JWTPrincipal?): String {
       return if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
           "Z9999992"
       } else {
           principal!!.payload.getClaim("NAVident").asString()
       }
   }
   ```

3. Start appen på nytt. Nå kan du attestere saken.

Bytt tilbake til `Z9999991` når du er ferdig, og ikke commit endringen.

## Med innlogging i Azure

Ved behov for "ekte" innlogging mot Azure kan dette aktiveres for lokal utvikling ved å endre konfigurasjonen i
filen `AuthenticationConfig.kt`.
For at dette skal fungere må man i tillegg bytte ut Azure-stubben i `StubClientsBuilder.kt` med en faktisk Azure-klient.

Bruk av faktisk klient krever miljøvariabelen:

* AZURE_APP_CLIENT_SECRET

Dette kan man enten legge inn i `Run Configuration` i IntelliJ eller lage en `.env` fil på rot-nivå. Se `.env.template`
som eksempel.

Spør en fra teamet, eller hent de fra `Vault` under team-secrets for  `supstonad`

## For testing mot Azure

Legg inn følgende variabler i [.env]():

- AZURE_APP_CLIENT_ID
- AZURE_APP_WELL_KNOWN_URL
- AZURE_APP_CLIENT_SECRET

Kan kopiere `AZURE_APP_WELL_KNOWN_URL` fra .env.azure.template
Disse hentes fra kjørende pod i det miljøet du vil teste mot.
Merk: `AZURE_APP_CLIENT_SECRET` roteres automatisk.

For da å bruke gruppe-claimet som kommer fra Azure må man også endre implementasjonen av `getGroupsFromJWT`
i [Extensions.kt](../common/infrastructure/src/main/kotlin/no/nav/su/se/bakover/common/infrastructure/web/Extensions.kt).

## For testing mot Sts lokalt

Bytt dette i AuthenticationConfig.kt

```diff
- verifier(jwkStsProvider, stsJwkConfig.getString("issuer"))
+ verifier(jwkStsProvider, "https://security-token-service.nais.preprod.local"))
```

Bytt dette i StsClient.kt

```diff
override fun jwkConfig(): JSONObject {
    val (_, _, result) = wellKnownUrl.httpGet().responseString()
    return result.fold(
-        { JSONObject(it) },
+        { JSONObject(it.replace("nais.preprod.local", "dev.adeo.no")) },
        { throw RuntimeException("Could not get JWK config from url $wellKnownUrl, error:$it") }
    )
}
```
