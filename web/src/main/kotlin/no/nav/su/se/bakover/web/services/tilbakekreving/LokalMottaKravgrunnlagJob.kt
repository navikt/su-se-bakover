package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Clock
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
                tilbakekrevingService.hentAvventerKravgrunnlag()
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
        return when (revurdering) {
            is IverksattRevurdering.IngenEndring -> {
                throw IllegalStateException("Kan ikke tilbakekreve for ingen endring")
            }
            is IverksattRevurdering.Innvilget -> {
                revurdering to revurdering.simulering
            }
            is IverksattRevurdering.Opphørt -> {
                revurdering to revurdering.simulering
            }
        }.let { (revurdering, simulering) ->
            lagKravgrunnlagXml(
                revurdering = revurdering,
                simulering = simulering,
            )
        }
    }

    fun lagKravgrunnlagXml(revurdering: IverksattRevurdering, simulering: Simulering): String {
        val kravgrunnlag = matchendeKravgrunnlag(
            revurdering = revurdering,
            simulering = simulering,
            clock = Clock.systemUTC(),
        )
        return KravgrunnlagMapper.toXml(
            KravmeldingRootDto(
                kravmeldingDto = KravmeldingDto(
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
                    tilbakekrevingsperioder = kravgrunnlag.grunnlagsperioder.map {
                        KravmeldingDto.Tilbakekrevingsperiode(
                            periode = KravmeldingDto.Tilbakekrevingsperiode.Periode(
                                fraOgMed = it.periode.fraOgMed.toString(),
                                tilOgMed = it.periode.tilOgMed.toString(),
                            ),
                            skattebeløpPerMåned = it.beløpSkattMnd.toString(),
                            tilbakekrevingsbeløp = it.grunnlagsbeløp.map {
                                KravmeldingDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
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
        ).getOrHandle { throw it }
    }
}

/**
 * TODO dobbeltimplementasjon
 * @see [no.nav.su.se.bakover.test.matchendeKravgrunnlag]
 */
fun matchendeKravgrunnlag(
    revurdering: Revurdering,
    simulering: Simulering,
    clock: Clock,
): Kravgrunnlag {
    return simulering.tolk().let {
        Kravgrunnlag(
            saksnummer = revurdering.saksnummer,
            kravgrunnlagId = "123456",
            vedtakId = "654321",
            kontrollfelt = Tidspunkt.now(clock).toOppdragTimestamp(),
            status = Kravgrunnlag.KravgrunnlagStatus.NY,
            behandler = NavIdentBruker.Saksbehandler("K231B433"),
            grunnlagsperioder = it.simulertePerioder
                .filter { it.harFeilutbetalinger() }
                .map { periode ->
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = Periode.create(
                            fraOgMed = periode.periode.fraOgMed,
                            tilOgMed = periode.periode.tilOgMed,
                        ),
                        beløpSkattMnd = BigDecimal(4395),
                        grunnlagsbeløp = listOf(
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.KL_KODE_FEIL_INNT,
                                type = KlasseType.FEIL,
                                beløpTidligereUtbetaling = BigDecimal.ZERO,
                                beløpNyUtbetaling = BigDecimal(periode.hentFeilutbetalteBeløp().sum()),
                                beløpSkalTilbakekreves = BigDecimal.ZERO,
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal.ZERO,
                            ),
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.SUUFORE,
                                type = KlasseType.YTEL,
                                beløpTidligereUtbetaling = BigDecimal(periode.hentUtbetaltBeløp().sum()),
                                beløpNyUtbetaling = BigDecimal(periode.hentØnsketUtbetaling().sum()),
                                beløpSkalTilbakekreves = BigDecimal(periode.hentFeilutbetalteBeløp().sum()),
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal(43.9983),
                            ),
                        ),
                    )
                },
        )
    }
}
