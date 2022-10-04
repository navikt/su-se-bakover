package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.okonomi.tilbakekrevingservice.KravgrunnlagAnnulerRequest
import no.nav.okonomi.tilbakekrevingservice.KravgrunnlagAnnulerResponse
import no.nav.okonomi.tilbakekrevingservice.KravgrunnlagHentDetaljRequest
import no.nav.okonomi.tilbakekrevingservice.KravgrunnlagHentDetaljResponse
import no.nav.okonomi.tilbakekrevingservice.KravgrunnlagHentListeRequest
import no.nav.okonomi.tilbakekrevingservice.KravgrunnlagHentListeResponse
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingPortType
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakRequest
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakResponse
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåTilbakekrevingsvedtakForsendelse
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingsvedtakForsendelseFeil
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test

internal class TilbakekrevingSoapClientTest {

    @Test
    fun `svarer med feil hvis problemer hos mottaker`() {
        TilbakekrevingSoapClient(
            tilbakekrevingPortType = object : TilbakekrevingPortType {
                override fun tilbakekrevingsvedtak(p0: TilbakekrevingsvedtakRequest?): TilbakekrevingsvedtakResponse {
                    return TilbakekrevingSoapClientMapper.fromXml(
                        lagResponse(
                            alvorlighetsgrad = TilbakekrevingSoapClient.Alvorlighetsgrad.ALVORLIG_FEIL,
                        ),
                    )
                }

                override fun kravgrunnlagHentListe(p0: KravgrunnlagHentListeRequest?): KravgrunnlagHentListeResponse {
                    TODO("Not yet implemented")
                }

                override fun kravgrunnlagHentDetalj(p0: KravgrunnlagHentDetaljRequest?): KravgrunnlagHentDetaljResponse {
                    TODO("Not yet implemented")
                }

                override fun kravgrunnlagAnnuler(p0: KravgrunnlagAnnulerRequest?): KravgrunnlagAnnulerResponse {
                    TODO("Not yet implemented")
                }
            },
            clock = fixedClock,
        ).sendTilbakekrevingsvedtak(
            Tilbakekrevingsvedtak.FullTilbakekreving(
                vedtakId = "12345",
                ansvarligEnhet = "1111",
                kontrollFelt = "2222",
                behandler = NavIdentBruker.Saksbehandler("jab"),
                tilbakekrevingsperioder = listOf(),
            ),
        ) shouldBe TilbakekrevingsvedtakForsendelseFeil.left()
    }

    @Test
    fun `svarer med feil dersom exception kastes`() {
        TilbakekrevingSoapClient(
            tilbakekrevingPortType = object : TilbakekrevingPortType {
                override fun tilbakekrevingsvedtak(p0: TilbakekrevingsvedtakRequest?): TilbakekrevingsvedtakResponse {
                    throw RuntimeException("Et eller annet gærnt")
                }

                override fun kravgrunnlagHentListe(p0: KravgrunnlagHentListeRequest?): KravgrunnlagHentListeResponse {
                    TODO("Not yet implemented")
                }

                override fun kravgrunnlagHentDetalj(p0: KravgrunnlagHentDetaljRequest?): KravgrunnlagHentDetaljResponse {
                    TODO("Not yet implemented")
                }

                override fun kravgrunnlagAnnuler(p0: KravgrunnlagAnnulerRequest?): KravgrunnlagAnnulerResponse {
                    TODO("Not yet implemented")
                }
            },
            clock = fixedClock,
        ).sendTilbakekrevingsvedtak(
            Tilbakekrevingsvedtak.FullTilbakekreving(
                vedtakId = "12345",
                ansvarligEnhet = "1111",
                kontrollFelt = "2222",
                behandler = NavIdentBruker.Saksbehandler("jab"),
                tilbakekrevingsperioder = listOf(),
            ),
        ) shouldBe TilbakekrevingsvedtakForsendelseFeil.left()
    }

    @Test
    fun happy() {
        var requestXml = ""
        var responseXml = ""

        TilbakekrevingSoapClient(
            tilbakekrevingPortType = object : TilbakekrevingPortType {
                override fun tilbakekrevingsvedtak(p0: TilbakekrevingsvedtakRequest?): TilbakekrevingsvedtakResponse {
                    requestXml = TilbakekrevingSoapClientMapper.toXml(p0!!)
                    val response = TilbakekrevingSoapClientMapper.fromXml(
                        lagResponse(
                            alvorlighetsgrad = TilbakekrevingSoapClient.Alvorlighetsgrad.OK,
                        ),
                    )
                    responseXml = TilbakekrevingSoapClientMapper.toXml(response)
                    return response
                }

                override fun kravgrunnlagHentListe(p0: KravgrunnlagHentListeRequest?): KravgrunnlagHentListeResponse {
                    TODO("Not yet implemented")
                }

                override fun kravgrunnlagHentDetalj(p0: KravgrunnlagHentDetaljRequest?): KravgrunnlagHentDetaljResponse {
                    TODO("Not yet implemented")
                }

                override fun kravgrunnlagAnnuler(p0: KravgrunnlagAnnulerRequest?): KravgrunnlagAnnulerResponse {
                    TODO("Not yet implemented")
                }
            },
            clock = fixedClock,
        ).sendTilbakekrevingsvedtak(
            Tilbakekrevingsvedtak.FullTilbakekreving(
                vedtakId = "12345",
                ansvarligEnhet = "1111",
                kontrollFelt = "2222",
                behandler = NavIdentBruker.Saksbehandler("jab"),
                tilbakekrevingsperioder = listOf(),
            ),
        ) shouldBe RåTilbakekrevingsvedtakForsendelse(
            requestXml = requestXml,
            tidspunkt = Tidspunkt.now(fixedClock),
            responseXml = responseXml,
        ).right()
    }

    //language=xml
    private fun lagResponse(
        alvorlighetsgrad: TilbakekrevingSoapClient.Alvorlighetsgrad,
    ): String {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
              <tilbakekrevingsvedtakResponse xmlns="http://okonomi.nav.no/tilbakekrevingService/">
                 <mmel xmlns="">
                    <systemId xmlns="urn:no:nav:tilbakekreving:typer:v1">231-OPPD</systemId>
                    <kodeMelding xmlns="urn:no:nav:tilbakekreving:typer:v1">B441012F</kodeMelding>
                    <alvorlighetsgrad xmlns="urn:no:nav:tilbakekreving:typer:v1">$alvorlighetsgrad</alvorlighetsgrad>
                    <beskrMelding xmlns="urn:no:nav:tilbakekreving:typer:v1">Oppgitt vedtak-id finnes ikke/har feil status: 0000436207</beskrMelding>
                    <sqlKode xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <sqlState xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <sqlMelding xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <mqCompletionKode xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <mqReasonKode xmlns="urn:no:nav:tilbakekreving:typer:v1"/>
                    <programId xmlns="urn:no:nav:tilbakekreving:typer:v1">K231B441</programId>
                    <sectionNavn xmlns="urn:no:nav:tilbakekreving:typer:v1">CA10-VALIDER-INPUT</sectionNavn>
                 </mmel>
                 <tilbakekrevingsvedtak xmlns="">
                    <kodeAksjon xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"></kodeAksjon>
                    <vedtakId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">0</vedtakId>
                    <kodeHjemmel xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"/>
                    <enhetAnsvarlig xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"/>
                    <kontrollfelt xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"/>
                    <saksbehId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1"/>
                 </tilbakekrevingsvedtak>
              </tilbakekrevingsvedtakResponse>
        """.trimIndent()
    }
}
