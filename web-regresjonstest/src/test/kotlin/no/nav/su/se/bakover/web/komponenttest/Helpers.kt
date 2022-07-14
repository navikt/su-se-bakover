package no.nav.su.se.bakover.web.komponenttest

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.toYtelsekode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.web.services.tilbakekreving.TilbakekrevingsmeldingMapper
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringConsumer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponse
import java.math.BigDecimal

internal fun AppComponents.mottaKvitteringForUtbetalingFraØkonomi(utbetalingId: UUID30): String {
    return services.utbetaling.hentUtbetaling(utbetalingId).getOrFail()
        .shouldBeType<Utbetaling.OversendtUtbetaling.UtenKvittering>().let {
            lagUtbetalingsKvittering(it.utbetalingsrequest)
        }.also {
            consumers.utbetalingKvitteringConsumer.onMessage(it)
        }
}

internal fun AppComponents.sendTilbakekrevingsvedtakTilØkonomi() {
    services.tilbakekrevingService.sendTilbakekrevingsvedtak {
        TilbakekrevingsmeldingMapper.toKravgrunnlg(it).getOrFail()
    }
}

internal fun lagUtbetalingsKvittering(utbetalingsrequest: Utbetalingsrequest): String {
    val request = UtbetalingKvitteringConsumer.xmlMapper.readValue<UtbetalingRequest>(utbetalingsrequest.value)
    val kvittering = UtbetalingKvitteringResponse.Mmel(
        systemId = null,
        kodeMelding = null,
        alvorlighetsgrad = UtbetalingKvitteringResponse.Alvorlighetsgrad.OK,
        beskrMelding = null,
        sqlKode = null,
        sqlState = null,
        sqlMelding = null,
        mqCompletionKode = null,
        mqReasonKode = null,
        programId = null,
        sectionNavn = null,

    )
    return UtbetalingKvitteringConsumer.xmlMapper.writeValueAsString(
        UtbetalingKvitteringResponse(
            kvittering,
            request.oppdragRequest,
        ),
    )
}

internal fun lagKravgrunnlag(
    vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering,
    lagPerioder: () -> String,
): String {
    return """
        <urn:detaljertKravgrunnlagMelding xmlns:mmel="urn:no:nav:tilbakekreving:typer:v1" xmlns:urn="urn:no:nav:tilbakekreving:kravgrunnlag:detalj:v1">
            <urn:detaljertKravgrunnlag>
                <urn:kravgrunnlagId>298606</urn:kravgrunnlagId>
                <urn:vedtakId>436206</urn:vedtakId>
                <urn:kodeStatusKrav>NY</urn:kodeStatusKrav>
                <urn:kodeFagomraade>${vedtak.behandling.sakstype.toYtelsekode()}</urn:kodeFagomraade>
                <urn:fagsystemId>${vedtak.behandling.saksnummer}</urn:fagsystemId>
                <urn:vedtakIdOmgjort>0</urn:vedtakIdOmgjort>
                <urn:vedtakGjelderId>${vedtak.behandling.fnr}</urn:vedtakGjelderId>
                <urn:typeGjelderId>PERSON</urn:typeGjelderId>
                <urn:utbetalesTilId>${vedtak.behandling.fnr}</urn:utbetalesTilId>
                <urn:typeUtbetId>PERSON</urn:typeUtbetId>
                <urn:enhetAnsvarlig>8020</urn:enhetAnsvarlig>
                <urn:enhetBosted>8020</urn:enhetBosted>
                <urn:enhetBehandl>8020</urn:enhetBehandl>
                <urn:kontrollfelt>2022-02-07-18.39.47.693011</urn:kontrollfelt>
                <urn:saksbehId>K231B433</urn:saksbehId>
                <urn:referanse>${vedtak.utbetalingId}</urn:referanse>
                ${lagPerioder()}
            </urn:detaljertKravgrunnlag>
        </urn:detaljertKravgrunnlagMelding><?xml version="1.0" encoding="utf-8"?>
    """
}

internal fun lagKravgrunnlagPerioder(feilutbetalinger: List<Feilutbetaling>): String {
    return StringBuffer().apply {
        feilutbetalinger.forEach {
            append(
                """
                <urn:tilbakekrevingsPeriode>
                    <urn:periode>
                        <mmel:fom>${it.måned.fraOgMed}</mmel:fom>
                        <mmel:tom>${it.måned.tilOgMed}</mmel:tom>
                    </urn:periode>
                    <urn:belopSkattMnd>${it.skattMnd}</urn:belopSkattMnd>
                    <urn:tilbakekrevingsBelop>
                        <urn:kodeKlasse>KL_KODE_FEIL_INNT</urn:kodeKlasse>
                        <urn:typeKlasse>FEIL</urn:typeKlasse>
                        <urn:belopOpprUtbet>0.00</urn:belopOpprUtbet>
                        <urn:belopNy>${it.feilutbetalt()}</urn:belopNy>
                        <urn:belopTilbakekreves>0.00</urn:belopTilbakekreves>
                        <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                        <urn:skattProsent>0.0000</urn:skattProsent>
                    </urn:tilbakekrevingsBelop>
                    <urn:tilbakekrevingsBelop>
                        <urn:kodeKlasse>SUUFORE</urn:kodeKlasse>
                        <urn:typeKlasse>YTEL</urn:typeKlasse>
                        <urn:belopOpprUtbet>${it.gammelUtbetaling}</urn:belopOpprUtbet>
                        <urn:belopNy>${it.nyUtbetaling}</urn:belopNy>
                        <urn:belopTilbakekreves>${it.feilutbetalt()}</urn:belopTilbakekreves>
                        <urn:belopUinnkrevd>0.00</urn:belopUinnkrevd>
                        <urn:skattProsent>${it.skattProsent}</urn:skattProsent>
                    </urn:tilbakekrevingsBelop>
            </urn:tilbakekrevingsPeriode>
                """.trimIndent(),
            )
        }
    }.toString()
}

internal data class Feilutbetaling(
    val måned: Måned,
    var gammelUtbetaling: Int,
    val nyUtbetaling: Int,
    val skattMnd: BigDecimal = BigDecimal("4729.00"),
    val skattProsent: BigDecimal = BigDecimal("43.9983"),
) {
    fun feilutbetalt() = gammelUtbetaling - nyUtbetaling
}
