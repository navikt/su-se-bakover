package no.nav.su.se.bakover.web.dokumenter

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.Distribusjonstype
import no.nav.su.se.bakover.web.routes.sak.DokumentBody

fun opprettFritekstDokument(
    sakId: String,
    tittel: String = "Fritekst-brevets tittel",
    fritekst: String = "Innholdet i fritekst-brevet",
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    client: HttpClient,
): String {
    val body = serialize(
        DokumentBody(
            tittel = tittel,
            fritekst = fritekst,
            adresse = null,
            distribusjonstype = Distribusjonstype.ANNET,
        ),
    )
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/fritekstDokument",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody(body) }.apply {
            status shouldBe expectedHttpStatusCode
        }.bodyAsText()
    }
}
