package no.nav.su.se.bakover.web.utenlandsopphold

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.UtenlandsoppholdDokumentasjonJson
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.registrer.RegistrerUtenlandsoppholdJson

fun nyttUtenlandsopphold(
    sakId: String,
    fraOgMed: String = "2021-05-05",
    tilOgMed: String = "2021-10-10",
    journalposter: List<String> = listOf("1234567"),
    dokumentasjon: UtenlandsoppholdDokumentasjonJson = UtenlandsoppholdDokumentasjonJson.Sannsynliggjort,
    begrunnelse: String = "Har sendt inn kopi av flybiletter. Se journalpost",
    saksversjon: Long = 1,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/utenlandsopphold",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody(
                serialize(
                    RegistrerUtenlandsoppholdJson(
                        periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                        journalposter = journalposter,
                        dokumentasjon = dokumentasjon,
                        begrunnelse = begrunnelse,
                        saksversjon = saksversjon,
                    ),
                ),
            )
        }.apply {
            status shouldBe expectedHttpStatusCode
        }.bodyAsText()
    }
}
