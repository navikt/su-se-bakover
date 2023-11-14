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
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.RåttKravgrunnlagService
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.presentation.consumer.KravgrunnlagDto
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import tilbakekreving.presentation.consumer.KravgrunnlagRootDto
import økonomi.domain.simulering.Simulering
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class LokalMottaKravgrunnlagJob(
    private val initialDelay: Duration,
    private val intervall: Duration = Duration.ofMinutes(1),
    private val sessionFactory: SessionFactory,
    private val service: RåttKravgrunnlagService,
    private val clock: Clock,
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
                lagreRåttKravgrunnlagDetaljerForUtbetalingerSomMangler(
                    sessionFactory = sessionFactory,
                    service = service,
                    clock = clock,
                )
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}

/**
 * Denne kan gjenbrukes fra regresjonstester.
 *
 * @param overstyrUtbetalingId er ment for å trigge mismatch mellom kravgrunnlag og utbetaling. Dersom det er flere som mangler kravgrunnlag, må man sende for alle.
 */
fun lagreRåttKravgrunnlagDetaljerForUtbetalingerSomMangler(
    sessionFactory: SessionFactory,
    service: RåttKravgrunnlagService,
    clock: Clock,
    overstyrUtbetalingId: List<UUID30?>? = null,
) {
    withCorrelationId { correlationId ->
        finnSaksnummerOgUtbetalingIdSomManglerKravgrunnlag(sessionFactory).also {
            if (overstyrUtbetalingId != null) {
                require(it.size == overstyrUtbetalingId.size) {
                    "Dersom man ønsker overstyre utbetalingId, må man sende et element per kravgrunnlag som mangler. Kan sende null hvis man ikke vil overstyre et element."
                }
            }
        }.mapIndexed { index, (saksnummer, utbetalingId, simulering) ->
            lagKravgrunnlagDetaljerXml(
                saksnummer = saksnummer,
                simulering = simulering,
                utbetalingId = overstyrUtbetalingId?.get(index) ?: utbetalingId,
                clock = clock,
            )
        }.forEach {
            service.lagreRåttkravgrunnlagshendelse(
                råttKravgrunnlag = RåttKravgrunnlag(it),
                meta = JMSHendelseMetadata.fromCorrelationId(correlationId),
            )
        }
    }
}

fun lagKravgrunnlagDetaljerXml(
    saksnummer: Saksnummer,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
): String {
    // TODO jah: Litt rart at vi går via Kravgrunnlag for å emulere oppdrag sin xml
    val kravgrunnlag = genererKravgrunnlagFraSimulering(
        saksnummer = saksnummer,
        simulering = simulering,
        utbetalingId = utbetalingId,
        clock = clock,
        // Denne brukes ikke siden vi mapper til XML.
        kravgrunnlagPåSakHendelseId = HendelseId.generer(),
    )
    return lagKravgrunnlagDetaljerXml(
        kravgrunnlag = kravgrunnlag,
        fnr = simulering.gjelderId.toString(),
    )
}

fun lagKravgrunnlagDetaljerXml(
    kravgrunnlag: Kravgrunnlag,
    fnr: String,
): String {
    return KravgrunnlagDtoMapper.toXml(
        KravgrunnlagRootDto(
            kravgrunnlagDto = KravgrunnlagDto(
                kravgrunnlagId = kravgrunnlag.eksternKravgrunnlagId,
                vedtakId = kravgrunnlag.eksternVedtakId,
                kodeStatusKrav = kravgrunnlag.status.toDtoStatus(),
                kodeFagområde = "SUUFORE",
                fagsystemId = kravgrunnlag.saksnummer.toString(),
                datoVedtakFagsystem = null,
                vedtakIdOmgjort = null,
                vedtakGjelderId = fnr,
                idTypeGjelder = "PERSON",
                utbetalesTilId = fnr,
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
                        tilbakekrevingsbeløp = listOf(
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                                kodeKlasse = "SUUFORE",
                                typeKlasse = "YTEL",
                                belopOpprUtbet = it.ytelse.beløpTidligereUtbetaling.toString(),
                                belopNy = it.ytelse.beløpNyUtbetaling.toString(),
                                belopTilbakekreves = it.ytelse.beløpSkalTilbakekreves.toString(),
                                belopUinnkrevd = it.ytelse.beløpSkalIkkeTilbakekreves.toString(),
                                skattProsent = it.ytelse.skatteProsent.toString(),
                            ),
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                                kodeKlasse = "KL_KODE_FEIL_INNT",
                                typeKlasse = "FEIL",
                                belopOpprUtbet = it.feilutbetaling.beløpTidligereUtbetaling.toString(),
                                belopNy = it.feilutbetaling.beløpNyUtbetaling.toString(),
                                belopTilbakekreves = it.feilutbetaling.beløpSkalTilbakekreves.toString(),
                                belopUinnkrevd = it.feilutbetaling.beløpSkalIkkeTilbakekreves.toString(),
                                skattProsent = "0.0000",
                            ),
                        ),
                    )
                },
            ),
        ),
    ).getOrElse { throw it }
}

/**
 * TODO dobbeltimplementasjon i test
 * @see [no.nav.su.se.bakover.test.genererKravgrunnlagFraSimulering]
 *
 * @param clock Brukes til å sette default eksternTidspunkt
 */
fun genererKravgrunnlagFraSimulering(
    saksnummer: Saksnummer,
    simulering: Simulering,
    utbetalingId: UUID30,
    clock: Clock,
    eksternKravgrunnlagId: String = "123456",
    eksternVedtakId: String = "654321",
    behandler: String = "K231B433",
    eksternTidspunkt: Tidspunkt = Tidspunkt.now(clock),
    status: Kravgrunnlagstatus = Kravgrunnlagstatus.Nytt,
    skatteprosent: BigDecimal = BigDecimal("50"),
    kravgrunnlagPåSakHendelseId: HendelseId,
): Kravgrunnlag {
    return Kravgrunnlag(
        hendelseId = kravgrunnlagPåSakHendelseId,
        saksnummer = saksnummer,
        eksternKravgrunnlagId = eksternKravgrunnlagId,
        eksternVedtakId = eksternVedtakId,
        eksternKontrollfelt = eksternTidspunkt.toOppdragTimestamp(),
        status = status,
        behandler = behandler,
        utbetalingId = utbetalingId,
        eksternTidspunkt = eksternTidspunkt,
        grunnlagsmåneder = simulering.hentFeilutbetalteBeløp()
            .map { (måned, feilutbetaling) ->
                val beløpTidligereUtbetaling = simulering.hentUtbetalteBeløp(måned)!!.sum()
                val beløpNyUtbetaling = simulering.hentTotalUtbetaling(måned)!!.sum()
                val beløpSkalTilbakekreves = feilutbetaling.sum()
                require(beløpTidligereUtbetaling - beløpNyUtbetaling == beløpSkalTilbakekreves) {
                    "Forventet at beløpTidligereUtbetaling ($beløpTidligereUtbetaling) - beløpNyUtbetaling($beløpNyUtbetaling) == beløpSkalTilbakekreves($beløpSkalTilbakekreves)."
                }
                Kravgrunnlag.Grunnlagsmåned(
                    måned = måned,
                    betaltSkattForYtelsesgruppen = skatteprosent.times(BigDecimal(beløpSkalTilbakekreves)).divide(
                        BigDecimal(100.0000),
                    ).setScale(0, RoundingMode.UP),
                    ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
                        beløpTidligereUtbetaling = beløpTidligereUtbetaling,
                        beløpNyUtbetaling = beløpNyUtbetaling,
                        beløpSkalTilbakekreves = beløpSkalTilbakekreves,
                        beløpSkalIkkeTilbakekreves = 0,
                        skatteProsent = skatteprosent,
                    ),
                    feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                        beløpTidligereUtbetaling = 0,
                        beløpNyUtbetaling = simulering.hentFeilutbetalteBeløp(måned)!!.sum(),
                        beløpSkalTilbakekreves = 0,
                        beløpSkalIkkeTilbakekreves = 0,
                    ),
                )
            },
    )
}

private fun Kravgrunnlagstatus.toDtoStatus(): String = when (this) {
    Kravgrunnlagstatus.Annulert -> "ANNU"
    Kravgrunnlagstatus.AnnulertVedOmg -> "ANOM"
    Kravgrunnlagstatus.Avsluttet -> "AVSL"
    Kravgrunnlagstatus.Ferdigbehandlet -> "BEGA"
    Kravgrunnlagstatus.Endret -> "ENDR"
    Kravgrunnlagstatus.Feil -> "FEIL"
    Kravgrunnlagstatus.Manuell -> "MANU"
    Kravgrunnlagstatus.Nytt -> "NY"
    Kravgrunnlagstatus.Sperret -> "SPER"
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
          , distinct_utbetaling AS (
          select DISTINCT ON(u.id) s.saksnummer, u.id, u.simulering, u.opprettet from utbetaling u
          join sak s on u.sakid = s.id
          JOIN LATERAL jsonb_array_elements_text(simulering->'periodeList') pl ON TRUE
          JOIN LATERAL jsonb_array_elements_text((pl::jsonb)->'utbetaling') ut ON TRUE
          WHERE (ut::jsonb)->>'feilkonto' = 'true'
          AND u.id not in (SELECT extracted_value FROM xml_values where extracted_value is not null)
          ORDER BY u.id, u.opprettet
          )
          SELECT saksnummer, id, simulering 
          FROM distinct_utbetaling
          ORDER BY opprettet;
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
