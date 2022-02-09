package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import kotlin.concurrent.fixedRateTimer

internal class LokalMottaKravgrunnlagJob(
    private val tilbakekrevingConsumer: TilbakekrevingConsumer,
    private val tilbakekrevingService: TilbakekrevingService,
    private val revurderingService: RevurderingService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun schedule() {
        val delayISekunder = 10L
        log.error("Lokal jobb: Startet skedulert jobb for mottak av kravgrunnlag som kjører hvert $delayISekunder sekund")
        val jobName = "local-motta-kravgrunnlag"
        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = 1000L * delayISekunder,
        ) {
            Either.catch {
                tilbakekrevingService.hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag()
                    .map {
                        val revurdering = revurderingService.hentRevurdering(it.avgjort.revurderingId)
                        kravgrunnlag(revurdering = revurdering as IverksattRevurdering)
                    }.forEach {
                        tilbakekrevingConsumer.onMessage(it)
                    }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    // TODO utbedre med faktisk innhold fra revurdering/tilbakekrevingsbehandling
    private fun kravgrunnlag(revurdering: IverksattRevurdering): String {
        //language=XML
        return """
        <?xml version="1.0" encoding="utf-8"?>
            <urn:detaljertKravgrunnlagMelding xmlns:mmel="urn:no:nav:tilbakekreving:typer:v1"
                                              xmlns:urn="urn:no:nav:tilbakekreving:kravgrunnlag:detalj:v1">
                <urn:detaljertKravgrunnlag>
                    <urn:kravgrunnlagId>298604</urn:kravgrunnlagId>
                    <urn:vedtakId>436204</urn:vedtakId>
                    <urn:kodeStatusKrav>NY</urn:kodeStatusKrav>
                    <urn:kodeFagomraade>SUUFORE</urn:kodeFagomraade>
                    <urn:fagsystemId>${revurdering.saksnummer}</urn:fagsystemId>
                    <urn:vedtakIdOmgjort>0</urn:vedtakIdOmgjort>
                    <urn:vedtakGjelderId>${revurdering.fnr}</urn:vedtakGjelderId>
                    <urn:typeGjelderId>PERSON</urn:typeGjelderId>
                    <urn:utbetalesTilId>${revurdering.fnr}</urn:utbetalesTilId>
                    <urn:typeUtbetId>PERSON</urn:typeUtbetId>
                    <urn:enhetAnsvarlig>8020</urn:enhetAnsvarlig>
                    <urn:enhetBosted>8020</urn:enhetBosted>
                    <urn:enhetBehandl>8020</urn:enhetBehandl>
                    <urn:kontrollfelt>2022-02-07-18.39.46.586953</urn:kontrollfelt>
                    <urn:saksbehId>K231B433</urn:saksbehId>
                    <urn:tilbakekrevingsPeriode>
                        <urn:periode>
                            <mmel:fom>2021-10-01</mmel:fom>
                            <mmel:tom>2021-10-31</mmel:tom>
                        </urn:periode>
                        <urn:belopSkattMnd>4395.00</urn:belopSkattMnd>
                        <urn:tilbakekrevingsBelop>
                            <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                            <urn:typeKlasse>FEIL</urn:typeKlasse>
                            <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                            <urn:belopNy>9989.00</urn:belopNy>
                            <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                            <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                            <urn:skattProsent>0.0000</urn:skattProsent>
                        </urn:tilbakekrevingsBelop>
                        <urn:tilbakekrevingsBelop>
                            <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                            <urn:typeKlasse>YTEL</urn:typeKlasse>
                            <urn:belopOpprUtbet>9989.00</urn:belopOpprUtbet>
                            <urn:belopNy>0.00</urn:belopNy>
                            <urn:belopTilbakekreves>9989.00</urn:belopTilbakekreves>
                            <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                            <urn:skattProsent>43.9983</urn:skattProsent>
                        </urn:tilbakekrevingsBelop>
                    </urn:tilbakekrevingsPeriode>
                </urn:detaljertKravgrunnlag>
            </urn:detaljertKravgrunnlagMelding>
        """.trimIndent()
    }
}
