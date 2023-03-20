package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørtRevurdering
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class LokalMottaKravgrunnlagJob(
    private val tilbakekrevingConsumer: TilbakekrevingConsumer,
    private val tilbakekrevingService: TilbakekrevingService,
    private val vedtakService: VedtakService,
    private val initialDelay: Duration,
    private val intervall: Duration = Duration.ofMinutes(1),
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun schedule() {
        log.error("Lokal jobb: Startet skedulert jobb for mottak av kravgrunnlag som kjører med intervall $intervall")
        val jobName = "local-motta-kravgrunnlag"
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
        ) {
            Either.catch {
                CorrelationId.withCorrelationId {
                    tilbakekrevingService.hentAvventerKravgrunnlag()
                        .map {
                            vedtakService.hentForRevurderingId(it.avgjort.revurderingId)!!.let { vedtak ->
                                when (vedtak) {
                                    is VedtakInnvilgetRevurdering -> {
                                        kravgrunnlag(vedtak)
                                    }

                                    is VedtakOpphørtRevurdering -> {
                                        kravgrunnlag(vedtak)
                                    }

                                    else -> throw IllegalStateException("Tilbakekrevingsbehandling er kun relevant for innvilget og opphørt revurdering")
                                }
                            }
                        }.forEach {
                            tilbakekrevingConsumer.onMessage(it)
                        }
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private fun kravgrunnlag(vedtak: VedtakInnvilgetRevurdering): String {
        return lagKravgrunnlagXml(
            revurdering = vedtak.behandling,
            simulering = vedtak.simulering,
            utbetalingId = vedtak.utbetalingId,
        )
    }

    private fun kravgrunnlag(vedtak: VedtakOpphørtRevurdering): String {
        return lagKravgrunnlagXml(
            revurdering = vedtak.behandling,
            simulering = vedtak.simulering,
            utbetalingId = vedtak.utbetalingId,
        )
    }

    fun lagKravgrunnlagXml(revurdering: IverksattRevurdering, simulering: Simulering, utbetalingId: UUID30): String {
        val kravgrunnlag = matchendeKravgrunnlag(
            revurdering = revurdering,
            simulering = simulering,
            utbetalingId = utbetalingId,
            clock = Clock.systemUTC(),
        )
        return TilbakekrevingsmeldingMapper.toXml(
            KravgrunnlagRootDto(
                kravgrunnlagDto = KravgrunnlagDto(
                    kravgrunnlagId = kravgrunnlag.kravgrunnlagId,
                    vedtakId = kravgrunnlag.vedtakId,
                    kodeStatusKrav = kravgrunnlag.status.toString(),
                    kodeFagområde = "SUUFORE",
                    fagsystemId = revurdering.saksnummer.toString(),
                    datoVedtakFagsystem = null,
                    vedtakIdOmgjort = null,
                    vedtakGjelderId = revurdering.fnr.toString(),
                    idTypeGjelder = "PERSON",
                    utbetalesTilId = revurdering.fnr.toString(),
                    idTypeUtbet = "PERSON",
                    kodeHjemmel = "ANNET",
                    renterBeregnes = "N",
                    enhetAnsvarlig = "4815",
                    enhetBosted = "8020",
                    enhetBehandl = "4815",
                    kontrollfelt = kravgrunnlag.kontrollfelt,
                    saksbehId = kravgrunnlag.behandler.toString(),
                    utbetalingId = kravgrunnlag.utbetalingId.toString(),
                    tilbakekrevingsperioder = kravgrunnlag.grunnlagsperioder.map {
                        KravgrunnlagDto.Tilbakekrevingsperiode(
                            periode = KravgrunnlagDto.Tilbakekrevingsperiode.Periode(
                                fraOgMed = it.periode.fraOgMed.toString(),
                                tilOgMed = it.periode.tilOgMed.toString(),
                            ),
                            skattebeløpPerMåned = it.beløpSkattMnd.toString(),
                            tilbakekrevingsbeløp = it.grunnlagsbeløp.map {
                                KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                                    kodeKlasse = it.kode.toString(),
                                    typeKlasse = it.type.toString(),
                                    belopOpprUtbet = it.beløpTidligereUtbetaling.toString(),
                                    belopNy = it.beløpNyUtbetaling.toString(),
                                    belopTilbakekreves = it.beløpSkalTilbakekreves.toString(),
                                    belopUinnkrevd = it.beløpSkalIkkeTilbakekreves.toString(),
                                    skattProsent = it.skatteProsent.toString(),
                                )
                            },
                        )
                    },
                ),
            ),
        ).getOrElse { throw it }
    }
}

/**
 * TODO dobbeltimplementasjon
 * @see [no.nav.su.se.bakover.test.matchendeKravgrunnlag]
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
            kravgrunnlagId = "123456",
            vedtakId = "654321",
            kontrollfelt = Tidspunkt.now(clock).toOppdragTimestamp(),
            status = Kravgrunnlag.KravgrunnlagStatus.NY,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            utbetalingId = utbetalingId,
            grunnlagsperioder = it.hentFeilutbetalteBeløp()
                .map { (periode, feilutbetaling) ->
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = Periode.create(
                            fraOgMed = periode.fraOgMed,
                            tilOgMed = periode.tilOgMed,
                        ),
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

internal fun matchendeKravgrunnlagDto(
    revurdering: Revurdering,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
): KravgrunnlagRootDto {
    val kravgrunnlag = matchendeKravgrunnlag(
        revurdering = revurdering,
        simulering = simulering,
        utbetalingId = utbetalingId,
        clock = clock,
    )
    return KravgrunnlagRootDto(
        kravgrunnlagDto = KravgrunnlagDto(
            kravgrunnlagId = kravgrunnlag.kravgrunnlagId,
            vedtakId = kravgrunnlag.vedtakId,
            kodeStatusKrav = kravgrunnlag.status.toString(),
            kodeFagområde = "SUUFORE",
            fagsystemId = revurdering.saksnummer.toString(),
            datoVedtakFagsystem = null,
            vedtakIdOmgjort = null,
            vedtakGjelderId = revurdering.fnr.toString(),
            idTypeGjelder = "PERSON",
            utbetalesTilId = revurdering.fnr.toString(),
            idTypeUtbet = "PERSON",
            kodeHjemmel = "ANNET",
            renterBeregnes = "N",
            enhetAnsvarlig = "4815",
            enhetBosted = "8020",
            enhetBehandl = "4815",
            kontrollfelt = kravgrunnlag.kontrollfelt,
            saksbehId = kravgrunnlag.behandler.toString(),
            utbetalingId = kravgrunnlag.utbetalingId.toString(),
            tilbakekrevingsperioder = kravgrunnlag.grunnlagsperioder.map {
                KravgrunnlagDto.Tilbakekrevingsperiode(
                    periode = KravgrunnlagDto.Tilbakekrevingsperiode.Periode(
                        fraOgMed = it.periode.fraOgMed.toString(),
                        tilOgMed = it.periode.tilOgMed.toString(),
                    ),
                    skattebeløpPerMåned = it.beløpSkattMnd.toString(),
                    tilbakekrevingsbeløp = it.grunnlagsbeløp.map {
                        KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                            kodeKlasse = it.kode.toString(),
                            typeKlasse = it.type.toString(),
                            belopOpprUtbet = it.beløpTidligereUtbetaling.toString(),
                            belopNy = it.beløpNyUtbetaling.toString(),
                            belopTilbakekreves = it.beløpSkalTilbakekreves.toString(),
                            belopUinnkrevd = it.beløpSkalIkkeTilbakekreves.toString(),
                            skattProsent = it.skatteProsent.toString(),
                        )
                    },
                )
            },
        ),
    )
}
