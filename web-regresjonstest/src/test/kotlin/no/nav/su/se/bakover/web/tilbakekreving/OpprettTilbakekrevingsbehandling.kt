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
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.routes.tilbakekreving.opprett.OpprettTilbakekrevingRequest
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import java.util.UUID

/**
 * Oppretter en tilbakekrevingsbehandling for en gitt sak.
 * Kjører også konsumenten som lytter på disse hendelsene for å opprette en oppgave.
 */
internal fun AppComponents.opprettTilbakekrevingsbehandling(
    sakId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    utførSideeffekter: Boolean = true,
    hendelsesversjon: Long,
): OpprettetTilbakekrevingsbehandlingRespons {
    val appComponents = this
    val sakFørKallJson = hentSak(sakId, client)
    val tidligereUtførteSideeffekter = hentUtførteSideeffekter(sakId)
    return runBlocking {
        val correlationId = CorrelationId.generate()
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/ny",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
            correlationId = correlationId.toString(),
        ) { setBody(serialize(OpprettTilbakekrevingRequest(versjon = hendelsesversjon, relatertId = UUID.randomUUID().toString()))) }.apply {
            withClue("opprettTilbakekrevingsbehandling feilet: ${this.bodyAsText()}") {
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
                    antallOpprettetOppgaver = 1,
                )
                // Vi sletter statusen på jobben, men ikke selve oppgavehendelsen for å verifisere at vi ikke oppretter duplikate oppgaver i disse tilfellene.
                appComponents.slettOpprettetOppgaveKonsumentJobb()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallOpprettetOppgaver = 1,
                )
            }
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            val tilbakekrevingRespons = deserialize<TilbakekrevingsbehandlingJson>(responseJson)
            if (verifiserRespons) {
                if (utførSideeffekter) {
                    // oppgavehendelse
                    saksversjonEtter shouldBe hendelsesversjon + 2
                } else {
                    saksversjonEtter shouldBe hendelsesversjon + 1
                }
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                listOf(
                    tilbakekrevingRespons,
                    deserialize(JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString()),
                ).forEach {
                    it.shouldBeEqualToIgnoringFields(
                        lagOpprettTilbakekrevingRespons(
                            sakId,
                            Tidspunkt.now(fixedClock),
                            hendelsesversjon + 1,
                        ),
                        it::id,
                        it::opprettet,
                        it::kravgrunnlag,
                    )
                }
            }
            OpprettetTilbakekrevingsbehandlingRespons(
                tilbakekrevingsbehandlingId = tilbakekrevingRespons.id,
                saksversjon = saksversjonEtter,
                responseJson = tilbakekrevingRespons,
            )
        }
    }
}

internal data class OpprettetTilbakekrevingsbehandlingRespons(
    val tilbakekrevingsbehandlingId: String,
    val saksversjon: Long,
    val responseJson: TilbakekrevingsbehandlingJson,
)
