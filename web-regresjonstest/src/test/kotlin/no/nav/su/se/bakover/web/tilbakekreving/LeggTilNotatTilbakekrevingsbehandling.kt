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
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.presentation.api.common.ForhåndsvarselMetaInfoJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus
import tilbakekreving.presentation.api.common.VurderingerMedKravJson

object LeggTilNotatTilbakekrevingsbehandling {
    internal fun leggTilNotatTilbakekrevingsbehandling(
        sakId: String,
        tilbakekrevingsbehandlingId: String,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
        client: HttpClient,
        verifiserRespons: Boolean = true,
        saksversjon: Long,
        verifiserForhåndsvarselDokumenter: List<ForhåndsvarselMetaInfoJson>,
        verifiserVurderinger: VurderingerMedKravJson,
        verifiserFritekst: String = "Regresjonstest: Fritekst til vedtaksbrev under tilbakekrevingsbehandling.",
    ): LeggTilNotatTilbakekrevingsbehandlingRespons {
        // Muterer databasen, men ingen eksterne systemer.
        val sakFørKallJson = hentSak(sakId, client)
        return runBlocking {
            no.nav.su.se.bakover.test.application.defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/notat",
                listOf(Brukerrolle.Saksbehandler),
                client = client,
            ) {
                setBody("""{"versjon": $saksversjon, "notat": "notatet"}""")
            }.apply {
                withClue("Kunne ikke legge til notat til tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                    status shouldBe expectedHttpStatusCode
                }
            }.bodyAsText().let { responseJson ->
                val sakEtterKallJson = hentSak(sakId, client)
                val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
                val tilbakekrevingRespons = deserialize<TilbakekrevingsbehandlingJson>(responseJson)
                if (verifiserRespons) {
                    sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                    listOf(
                        tilbakekrevingRespons,
                        deserialize(
                            JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                        ),
                    ).forEach {
                        it.shouldBeEqualToIgnoringFields(
                            lagOpprettTilbakekrevingRespons(
                                sakId,
                                Tidspunkt.now(fixedClock),
                                saksversjon + 1,
                                status = TilbakekrevingsbehandlingStatus.VEDTAKSBREV,
                                fritekst = verifiserFritekst,
                                notat = "notatet",
                            ),
                            it::id,
                            it::opprettet,
                            it::kravgrunnlag,
                            it::forhåndsvarselsInfo,
                            it::vurderinger,
                        )

                        it.forhåndsvarselsInfo shouldBe verifiserForhåndsvarselDokumenter

                        it.vurderinger shouldBe verifiserVurderinger

                        it.kravgrunnlag!!.shouldBeEqualToIgnoringFields(
                            lagKravgrunnlagRespons(),
                            it.kravgrunnlag!!::hendelseId,
                            it.kravgrunnlag!!::kontrollfelt,
                        )
                        it.forhåndsvarselsInfo.size shouldBe 1
                    }
                }
                LeggTilNotatTilbakekrevingsbehandlingRespons(
                    notat = tilbakekrevingRespons.notat!!,
                    saksversjon = saksversjonEtter,
                )
            }
        }
    }
}

data class LeggTilNotatTilbakekrevingsbehandlingRespons(
    val notat: String,
    val saksversjon: Long,
)
