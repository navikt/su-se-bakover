package no.nav.su.se.bakover.web.komponenttest

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.utbetaling.kvittering.kvitteringXml
import økonomi.domain.simulering.toYtelsekode
import økonomi.domain.utbetaling.Utbetaling
import java.math.BigDecimal
import java.util.UUID

internal fun AppComponents.mottaKvitteringOgFerdigstillVedtak(sakId: UUID) {
    return services.utbetaling.hentUtbetalingerForSakId(sakId)
        .filterIsInstance<Utbetaling.OversendtUtbetaling.UtenKvittering>().forEach {
            mottaKvitteringOgFerdigstillVedtak(it.id)
        }
}

internal fun AppComponents.mottaKvitteringOgFerdigstillVedtak(utbetalingId: UUID30) {
    val correlationId = CorrelationId.generate()
    return databaseRepos.utbetaling.hentOversendtUtbetalingForUtbetalingId(utbetalingId, null)!!
        .shouldBeType<Utbetaling.OversendtUtbetaling.UtenKvittering>().let {
            utbetalingskvitteringKomponenter.råKvitteringService.lagreRåKvitteringshendelse(
                originalKvittering = kvitteringXml(it),
                meta = JMSHendelseMetadata.fromCorrelationId(
                    correlationId,
                ),
            )
            utbetalingskvitteringKomponenter.knyttKvitteringTilSakOgUtbetalingService.knyttKvitteringerTilSakOgUtbetaling(
                correlationId = correlationId,
            )
            utbetalingskvitteringKomponenter.ferdigstillVedtakEtterMottattKvitteringKonsument.ferdigstillVedtakEtterMottattKvittering()
        }
}

@Suppress("unused")
internal fun lagKravgrunnlag(
    vedtak: VedtakInnvilgetRevurdering,
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
                <urn:kontrollfelt>2021-01-01-02.02.03.456789</urn:kontrollfelt>
                <urn:saksbehId>K231B433</urn:saksbehId>
                <urn:referanse>${vedtak.utbetalingId}</urn:referanse>
                ${lagPerioder()}
            </urn:detaljertKravgrunnlag>
        </urn:detaljertKravgrunnlagMelding><?xml version="1.0" encoding="utf-8"?>
    """
}

@Suppress("unused")
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
