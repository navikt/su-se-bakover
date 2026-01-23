package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus

internal fun AppComponents.avbrytTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    utførSideeffekter: Boolean = true,
    saksversjon: Long,
): AvbrytTilbakekrevingRespons {
    val appComponents = this
    val sakFørKallJson = hentSak(sakId, client)
    val tidligereUtførteSideeffekter = hentUtførteSideeffekter(sakId)
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/avbryt",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody("""{"versjon": $saksversjon, "fritekst": "friteksten", "begrunnelse": "begrunnelsen", "skalSendeBrev": "SKAL_SENDE_BREV_MED_FRITEKST"}""")
        }.apply {
            withClue("Kunne ikke avbryte tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let { responseJson ->
            if (utførSideeffekter) {
                // Vi kjører konsumentene 2 ganger, for å se at vi ikke oppretter duplikate oppgaver.
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallLukketOppgaver = 1,
                )
                // Vi sletter statusen på jobben, men ikke selve oppgavehendelsen for å verifisere at vi ikke oppretter duplikate oppgaver i disse tilfellene.
                appComponents.slettLukketOppgaveKonsumentJobb()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallLukketOppgaver = 1,
                )
            }
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            val tilbakekrevingRespons = deserialize<TilbakekrevingsbehandlingJson>(responseJson)
            if (verifiserRespons) {
                if (utførSideeffekter) {
                    // oppgavehendelse
                    saksversjonEtter shouldBe saksversjon + 2
                } else {
                    saksversjonEtter shouldBe saksversjon + 1
                }
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                listOf(
                    tilbakekrevingRespons,
                    deserialize<TilbakekrevingsbehandlingJson>(
                        JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                    ),
                ).forEach {
                    it.shouldBeEqualToIgnoringFields(
                        lagOpprettTilbakekrevingRespons(
                            sakId,
                            Tidspunkt.now(fixedClock),
                            saksversjon + 1,
                            status = TilbakekrevingsbehandlingStatus.AVBRUTT,
                        ),
                        it::id,
                        it::opprettet,
                        it::kravgrunnlag,
                        it::avsluttetTidspunkt,
                    )
                }
            }
            AvbrytTilbakekrevingRespons(
                responseJson = tilbakekrevingRespons,
                saksversjon = saksversjonEtter,
            )
        }
    }
}

data class AvbrytTilbakekrevingRespons(
    val responseJson: TilbakekrevingsbehandlingJson,
    val saksversjon: Long,
)
