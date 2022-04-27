package no.nav.su.se.bakover.web.søknadsbehandling.opphold

import io.ktor.client.utils.EmptyContent.status
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest
import org.junit.jupiter.api.Assertions

/**
- [vurdering] se [no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.LovligOpphold.Status]
 */
internal fun ApplicationTestBuilder.leggTilLovligOppholdINorge(
    sakId: String,
    behandlingId: String,
    vurdering: String = "VilkårOppfylt",
    begrunnelse: String = "Vurdering av lovligOppholdINorge er lagt til automatisk av LeggTilLovligOppholdINorge.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    val url = "/saker/$sakId/behandlinger/$behandlingId/informasjon"

    testApplication {
        application {
            testSusebakover()
        }
        defaultRequest(HttpMethod.Get, url).apply {
            Assertions.assertEquals(HttpStatusCode.Unauthorized, this.status)
        }
    }
/*
    return defaultRequest(
        HttpMethod.Patch,
        "/saker/$sakId/behandlinger/$behandlingId/informasjon",
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            //language=JSON
            """
                  {
                    "flyktning":null,
                    "lovligOpphold":{
                      "status":"$vurdering",
                      "begrunnelse":"$begrunnelse"
                    },
                    "fastOppholdINorge":null,
                    "institusjonsopphold":null,
                    "formue":null,
                    "personligOppmøte":null
                  }
            """.trimIndent(),
        )
    }.apply {
        status shouldBe HttpStatusCode.OK
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
    */
}
