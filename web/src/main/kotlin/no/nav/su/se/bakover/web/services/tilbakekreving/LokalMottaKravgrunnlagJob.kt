package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.uuid30
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.simulering.deserializeSimulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.RåttKravgrunnlagService
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.presentation.consumer.KravgrunnlagDto
import tilbakekreving.presentation.consumer.KravgrunnlagRootDto
import tilbakekreving.presentation.consumer.TilbakekrevingsmeldingMapper
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class LokalMottaKravgrunnlagJob(
    private val initialDelay: Duration,
    private val intervall: Duration = Duration.ofMinutes(1),
    private val sessionFactory: SessionFactory,
    private val service: RåttKravgrunnlagService,
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
                withCorrelationId { correlationId ->
                    finnSaksnummerOgUtbetalingIdSomManglerKravgrunnlag(sessionFactory).map { (saksnummer, utbetalingId, simulering) ->
                        lagKravgrunnlagXml(
                            saksnummer = saksnummer,
                            simulering = simulering,
                            utbetalingId = utbetalingId,
                        )
                    }.forEach {
                        service.lagreRåKvitteringshendelse(
                            råttKravgrunnlag = RåttKravgrunnlag(it),
                            meta = JMSHendelseMetadata.fromCorrelationId(correlationId),
                        )
                    }
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    fun lagKravgrunnlagXml(
        saksnummer: Saksnummer,
        simulering: Simulering,
        utbetalingId: UUID30,
    ): String {
        val kravgrunnlag = matchendeKravgrunnlag(
            saksnummer = saksnummer,
            simulering = simulering,
            utbetalingId = utbetalingId,
            clock = Clock.systemUTC(),
        )
        return TilbakekrevingsmeldingMapper.toXml(
            KravgrunnlagRootDto(
                kravgrunnlagDto = KravgrunnlagDto(
                    kravgrunnlagId = kravgrunnlag.eksternKravgrunnlagId,
                    vedtakId = kravgrunnlag.eksternVedtakId,
                    kodeStatusKrav = kravgrunnlag.status.toDtoStatus(),
                    kodeFagområde = "SUUFORE",
                    fagsystemId = saksnummer.toString(),
                    datoVedtakFagsystem = null,
                    vedtakIdOmgjort = null,
                    vedtakGjelderId = simulering.gjelderId.toString(),
                    idTypeGjelder = "PERSON",
                    utbetalesTilId = simulering.gjelderId.toString(),
                    idTypeUtbet = "PERSON",
                    kodeHjemmel = "ANNET",
                    renterBeregnes = "N",
                    enhetAnsvarlig = "4815",
                    enhetBosted = "8020",
                    enhetBehandl = "4815",
                    kontrollfelt = kravgrunnlag.eksternKontrollfelt,
                    saksbehId = kravgrunnlag.behandler,
                    utbetalingId = kravgrunnlag.utbetalingId.toString(),
                    tilbakekrevingsperioder = kravgrunnlag.grunnlagsmåneder.map {
                        KravgrunnlagDto.Tilbakekrevingsperiode(
                            periode = KravgrunnlagDto.Tilbakekrevingsperiode.Periode(
                                fraOgMed = it.måned.fraOgMed.toString(),
                                tilOgMed = it.måned.tilOgMed.toString(),
                            ),
                            skattebeløpPerMåned = it.betaltSkattForYtelsesgruppen.toString(),
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
    saksnummer: Saksnummer,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
): Kravgrunnlag {
    return simulering.let {
        Kravgrunnlag(
            saksnummer = saksnummer,
            eksternKravgrunnlagId = "123456",
            eksternVedtakId = "654321",
            eksternKontrollfelt = Tidspunkt.now(clock).toOppdragTimestamp(),
            status = Kravgrunnlag.KravgrunnlagStatus.Nytt,
            behandler = "K231B433",
            utbetalingId = utbetalingId,
            grunnlagsmåneder = it.hentFeilutbetalteBeløp()
                .map { (måned, feilutbetaling) ->
                    Kravgrunnlag.Grunnlagsmåned(
                        måned = måned,
                        betaltSkattForYtelsesgruppen = BigDecimal(4395),
                        grunnlagsbeløp = listOf(
                            Kravgrunnlag.Grunnlagsmåned.Grunnlagsbeløp(
                                kode = KlasseKode.KL_KODE_FEIL_INNT,
                                type = KlasseType.FEIL,
                                beløpTidligereUtbetaling = BigDecimal.ZERO,
                                beløpNyUtbetaling = BigDecimal(feilutbetaling.sum()),
                                beløpSkalTilbakekreves = BigDecimal.ZERO,
                                beløpSkalIkkeTilbakekreves = BigDecimal.ZERO,
                                skatteProsent = BigDecimal.ZERO,
                            ),
                            Kravgrunnlag.Grunnlagsmåned.Grunnlagsbeløp(
                                kode = KlasseKode.SUUFORE,
                                type = KlasseType.YTEL,
                                beløpTidligereUtbetaling = BigDecimal(it.hentUtbetalteBeløp(måned)!!.sum()),
                                beløpNyUtbetaling = BigDecimal(it.hentTotalUtbetaling(måned)!!.sum()),
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
    saksnummer: Saksnummer,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
): KravgrunnlagRootDto {
    val kravgrunnlag = matchendeKravgrunnlag(
        saksnummer = saksnummer,
        simulering = simulering,
        utbetalingId = utbetalingId,
        clock = clock,
    )
    return KravgrunnlagRootDto(
        kravgrunnlagDto = KravgrunnlagDto(
            kravgrunnlagId = kravgrunnlag.eksternKravgrunnlagId,
            vedtakId = kravgrunnlag.eksternVedtakId,
            kodeStatusKrav = kravgrunnlag.status.toDtoStatus(),
            kodeFagområde = "SUUFORE",
            fagsystemId = saksnummer.toString(),
            datoVedtakFagsystem = null,
            vedtakIdOmgjort = null,
            vedtakGjelderId = simulering.gjelderId.toString(),
            idTypeGjelder = "PERSON",
            utbetalesTilId = simulering.gjelderId.toString(),
            idTypeUtbet = "PERSON",
            kodeHjemmel = "ANNET",
            renterBeregnes = "N",
            enhetAnsvarlig = "4815",
            enhetBosted = "8020",
            enhetBehandl = "4815",
            kontrollfelt = kravgrunnlag.eksternKontrollfelt,
            saksbehId = kravgrunnlag.behandler,
            utbetalingId = kravgrunnlag.utbetalingId.toString(),
            tilbakekrevingsperioder = kravgrunnlag.grunnlagsmåneder.map {
                KravgrunnlagDto.Tilbakekrevingsperiode(
                    periode = KravgrunnlagDto.Tilbakekrevingsperiode.Periode(
                        fraOgMed = it.måned.fraOgMed.toString(),
                        tilOgMed = it.måned.tilOgMed.toString(),
                    ),
                    skattebeløpPerMåned = it.betaltSkattForYtelsesgruppen.toString(),
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

private fun Kravgrunnlag.KravgrunnlagStatus.toDtoStatus(): String = when (this) {
    Kravgrunnlag.KravgrunnlagStatus.Annulert -> "ANNU"
    Kravgrunnlag.KravgrunnlagStatus.AnnulertVedOmg -> "ANOM"
    Kravgrunnlag.KravgrunnlagStatus.Avsluttet -> "AVSL"
    Kravgrunnlag.KravgrunnlagStatus.Ferdigbehandlet -> "BEGA"
    Kravgrunnlag.KravgrunnlagStatus.Endret -> "ENDR"
    Kravgrunnlag.KravgrunnlagStatus.Feil -> "FEIL"
    Kravgrunnlag.KravgrunnlagStatus.Manuell -> "MANU"
    Kravgrunnlag.KravgrunnlagStatus.Nytt -> "NY"
    Kravgrunnlag.KravgrunnlagStatus.Sperret -> "SPER"
}

private data class KravgrunnlagData(
    val saksnummer: Saksnummer,
    val utbetalingId: UUID30,
    val simulering: Simulering,
)

private fun finnSaksnummerOgUtbetalingIdSomManglerKravgrunnlag(
    sessionFactory: SessionFactory,
): List<KravgrunnlagData> {
    return (sessionFactory as PostgresSessionFactory).withSession {
        """
            WITH xml_data AS (
                SELECT data->>'råttKravgrunnlag' AS xml_content
                FROM hendelse
                where type = 'MOTTATT_KRAVGRUNNLAG'
            )
            , xml_values AS (
                SELECT
                    (xpath(
                        '/detaljertKravgrunnlagMelding/detaljertKravgrunnlag/referanse/text()', 
                        xml_content::xml
                    ))[1]::text AS extracted_value
                FROM xml_data          
            )          
          select DISTINCT ON(u.id) s.saksnummer, u.id, u.simulering from utbetaling u
          join sak s on u.sakid = s.id
          JOIN LATERAL jsonb_array_elements_text(simulering->'periodeList') pl ON TRUE
          JOIN LATERAL jsonb_array_elements_text((pl::jsonb)->'utbetaling') ut ON TRUE
          WHERE (ut::jsonb)->>'feilkonto' = 'true'
          AND u.id not in (SELECT extracted_value FROM xml_values where extracted_value is not null)
          ORDER BY u.id;
        """.trimIndent().hentListe(
            params = emptyMap(),
            session = it,
        ) {
            KravgrunnlagData(
                saksnummer = Saksnummer(it.long("saksnummer")),
                utbetalingId = it.uuid30("id"),
                simulering = it.string("simulering").deserializeSimulering(),
            )
        }
    }
}
