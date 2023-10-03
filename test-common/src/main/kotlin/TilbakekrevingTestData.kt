package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import java.math.BigDecimal
import java.time.Clock

/**
 * TODO dobbeltimplementasjon
 * @see [no.nav.su.se.bakover.web.services.tilbakekreving.matchendeKravgrunnlag]
 */
fun matchendeKravgrunnlag(
    revurdering: Revurdering,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
): Kravgrunnlag {
    return simulering.let {
        Kravgrunnlag(
            saksnummer = revurdering.saksnummer,
            eksternKravgrunnlagId = "123456",
            eksternVedtakId = "654321",
            eksternKontrollfelt = Tidspunkt.now(clock).toOppdragTimestamp(),
            status = Kravgrunnlag.KravgrunnlagStatus.Nytt,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            utbetalingId = utbetalingId,
            grunnlagsperioder = it.hentFeilutbetalteBeløp()
                .map { (periode, feilutbetaling) ->
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = periode.tilMåned(),
                        beløpSkattMnd = BigDecimal(4395),
                        grunnlagsbeløp = listOf(
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.KL_KODE_FEIL_INNT,
                                type = KlasseType.FEIL,
                                beløpTidligereUtbetaling = BigDecimal.ZERO,
                                beløpNyUtbetaling = BigDecimal(feilutbetaling.sum()),
                                beløpSkalTilbakekreves = BigDecimal.ZERO,
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal.ZERO,
                            ),
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.SUUFORE,
                                type = KlasseType.YTEL,
                                beløpTidligereUtbetaling = BigDecimal(it.hentUtbetalteBeløp(periode)!!.sum()),
                                beløpNyUtbetaling = BigDecimal(it.hentTotalUtbetaling(periode)!!.sum()),
                                beløpSkalTilbakekreves = BigDecimal(feilutbetaling.sum()),
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal("43.9983"),
                            ),
                        ),
                    )
                },
        )
    }
}

fun nyRåttKravgrunnlag(
    kravgrunnlagXml: String = "<detaljertKravgrunnlagMelding><detaljertKravgrunnlag><kravgrunnlagId>123456</kravgrunnlagId><vedtakId>654321</vedtakId><kodeStatusKrav>NY</kodeStatusKrav><kodeFagomraade>SUUFORE</kodeFagomraade><fagsystemId>10002099</fagsystemId><datoVedtakFagsystem/><vedtakIdOmgjort/><vedtakGjelderId>18506438140</vedtakGjelderId><typeGjelderId>PERSON</typeGjelderId><utbetalesTilId>18506438140</utbetalesTilId><typeUtbetId>PERSON</typeUtbetId><kodeHjemmel>ANNET</kodeHjemmel><renterBeregnes>N</renterBeregnes><enhetAnsvarlig>4815</enhetAnsvarlig><enhetBosted>8020</enhetBosted><enhetBehandl>4815</enhetBehandl><kontrollfelt>2023-09-19-10.01.03.842916</kontrollfelt><saksbehId>K231B433</saksbehId><referanse>ef8ac92a-ba30-414e-85f4-d1227c</referanse><tilbakekrevingsPeriode><periode><fom>2023-06-01</fom><tom>2023-06-30</tom></periode><belopSkattMnd>4395</belopSkattMnd><tilbakekrevingsBelop><kodeKlasse>KL_KODE_FEIL_INNT</kodeKlasse><typeKlasse>FEIL</typeKlasse><belopOpprUtbet>0</belopOpprUtbet><belopNy>2643</belopNy><belopTilbakekreves>0</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>0</skattProsent></tilbakekrevingsBelop><tilbakekrevingsBelop><kodeKlasse>SUUFORE</kodeKlasse><typeKlasse>YTEL</typeKlasse><belopOpprUtbet>16181</belopOpprUtbet><belopNy>13538</belopNy><belopTilbakekreves>2643</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>43.9983</skattProsent></tilbakekrevingsBelop></tilbakekrevingsPeriode><tilbakekrevingsPeriode><periode><fom>2023-07-01</fom><tom>2023-07-31</tom></periode><belopSkattMnd>4395</belopSkattMnd><tilbakekrevingsBelop><kodeKlasse>KL_KODE_FEIL_INNT</kodeKlasse><typeKlasse>FEIL</typeKlasse><belopOpprUtbet>0</belopOpprUtbet><belopNy>2643</belopNy><belopTilbakekreves>0</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>0</skattProsent></tilbakekrevingsBelop><tilbakekrevingsBelop><kodeKlasse>SUUFORE</kodeKlasse><typeKlasse>YTEL</typeKlasse><belopOpprUtbet>16181</belopOpprUtbet><belopNy>13538</belopNy><belopTilbakekreves>2643</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>43.9983</skattProsent></tilbakekrevingsBelop></tilbakekrevingsPeriode><tilbakekrevingsPeriode><periode><fom>2023-08-01</fom><tom>2023-08-31</tom></periode><belopSkattMnd>4395</belopSkattMnd><tilbakekrevingsBelop><kodeKlasse>KL_KODE_FEIL_INNT</kodeKlasse><typeKlasse>FEIL</typeKlasse><belopOpprUtbet>0</belopOpprUtbet><belopNy>2643</belopNy><belopTilbakekreves>0</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>0</skattProsent></tilbakekrevingsBelop><tilbakekrevingsBelop><kodeKlasse>SUUFORE</kodeKlasse><typeKlasse>YTEL</typeKlasse><belopOpprUtbet>16181</belopOpprUtbet><belopNy>13538</belopNy><belopTilbakekreves>2643</belopTilbakekreves><belopUinnkrevd>0</belopUinnkrevd><skattProsent>43.9983</skattProsent></tilbakekrevingsBelop></tilbakekrevingsPeriode></detaljertKravgrunnlag></detaljertKravgrunnlagMelding>",
): RåttKravgrunnlag = RåttKravgrunnlag(kravgrunnlagXml)

fun nyKravgrunnlag(
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    kravgrunnlagId: String = "123-456",
    vedtakId: String = "789-101",
    kontrollfelt: String = "19.09.2023.16:54",
    status: Kravgrunnlag.KravgrunnlagStatus = Kravgrunnlag.KravgrunnlagStatus.Manuell,
    behandler: NavIdentBruker = saksbehandler,
    utbetalingId: UUID30 = UUID30.randomUUID(),
    grunnlagsperioder: List<Kravgrunnlag.Grunnlagsperiode> = emptyList(),
): Kravgrunnlag {
    return Kravgrunnlag(
        saksnummer = saksnummer,
        eksternKravgrunnlagId = kravgrunnlagId,
        eksternVedtakId = vedtakId,
        eksternKontrollfelt = kontrollfelt,
        status = status,
        behandler = behandler,
        utbetalingId = utbetalingId,
        grunnlagsperioder = grunnlagsperioder,
    )
}
