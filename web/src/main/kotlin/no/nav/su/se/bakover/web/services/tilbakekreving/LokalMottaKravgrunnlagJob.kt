package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.getOrElse
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.uuid30
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.simulering.deserializeSimulering
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.kravgrunnlag.RåttKravgrunnlagService
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlag
import tilbakekreving.presentation.consumer.KravgrunnlagDto
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import tilbakekreving.presentation.consumer.KravgrunnlagRootDto
import økonomi.domain.Fagområde
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import økonomi.domain.simulering.Simulering
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration

internal class LokalMottaKravgrunnlagJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            intervall: Duration = Duration.ofMinutes(1),
            sessionFactory: SessionFactory,
            service: RåttKravgrunnlagService,
            clock: Clock,
        ): LokalMottaKravgrunnlagJob {
            val log = LoggerFactory.getLogger(LokalMottaKravgrunnlagJob::class.java)
            val jobName = "local-motta-kravgrunnlag"
            log.error("Lokal jobb: Startet skedulert jobb '$jobName' for mottak av kravgrunnlag som kjører med intervall $intervall")

            return startStoppableJob(
                jobName = jobName,
                initialDelay = initialDelay,
                intervall = intervall,
                log = log,
                runJobCheck = emptyList(),
            ) {
                lagreRåttKravgrunnlagDetaljerForUtbetalingerSomMangler(
                    sessionFactory = sessionFactory,
                    service = service,
                    clock = clock,
                )
            }.let { LokalMottaKravgrunnlagJob(it) }
        }
    }
}

/**
 * Denne kan gjenbrukes fra regresjonstester.
 *
 * Så når man iverksetter et vedtak som skal utbetales, legger vi en XML-request på oppdrag sin ibm kø.
 * lagreRåttKravgrunnlagDetaljerForUtbetalingerSomMangler går  igjennom alle utbetalinger som er "fake sendt" til OS og persisterer en tilhørende rå kvittering.
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
        }.mapIndexed { index, (sakstype, saksnummer, utbetalingId, simulering) ->
            lagKravgrunnlagDetaljerXml(
                sakstype = sakstype,
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
    sakstype: Sakstype,
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
        sakstype = sakstype,
        kravgrunnlag = kravgrunnlag,
        fnr = simulering.gjelderId.toString(),
    )
}

fun lagKravgrunnlagDetaljerXml(
    sakstype: Sakstype,
    kravgrunnlag: Kravgrunnlag,
    fnr: String,
): String {
    return KravgrunnlagDtoMapper.toXml(
        KravgrunnlagRootDto(
            kravgrunnlagDto = KravgrunnlagDto(
                kravgrunnlagId = kravgrunnlag.eksternKravgrunnlagId,
                vedtakId = kravgrunnlag.eksternVedtakId,
                kodeStatusKrav = kravgrunnlag.status.toDtoStatus(),
                kodeFagområde = if (sakstype == Sakstype.ALDER) Fagområde.SUALDER.name else Fagområde.SUUFORE.name,
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
                tilbakekrevingsperioder = kravgrunnlag.grunnlagsperioder.map {
                    KravgrunnlagDto.Tilbakekrevingsperiode(
                        periode = KravgrunnlagDto.Tilbakekrevingsperiode.Periode(
                            fraOgMed = it.periode.fraOgMed.toString(),
                            tilOgMed = it.periode.tilOgMed.toString(),
                        ),
                        skattebeløpPerMåned = it.betaltSkattForYtelsesgruppen.toString(),
                        tilbakekrevingsbeløp = listOf(
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                                kodeKlasse = if (sakstype == Sakstype.ALDER) KlasseKode.SUALDER.name else KlasseKode.SUUFORE.name,
                                typeKlasse = KlasseType.YTEL.name,
                                belopOpprUtbet = it.bruttoTidligereUtbetalt.toString(),
                                belopNy = it.bruttoNyUtbetaling.toString(),
                                belopTilbakekreves = it.bruttoFeilutbetaling.toString(),
                                belopUinnkrevd = "0.00",
                                skattProsent = it.skatteProsent.toString(),
                            ),
                            KravgrunnlagDto.Tilbakekrevingsperiode.Tilbakekrevingsbeløp(
                                kodeKlasse = if (sakstype == Sakstype.ALDER) KlasseKode.KL_KODE_FEIL.name else KlasseKode.KL_KODE_FEIL_INNT.name,
                                typeKlasse = KlasseType.FEIL.name,
                                belopOpprUtbet = "0.00",
                                belopNy = it.bruttoFeilutbetaling.toString(),
                                belopTilbakekreves = "0.00",
                                belopUinnkrevd = "0.00",
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
        grunnlagsperioder = simulering.hentFeilutbetalteBeløp()
            .map { (måned, feilutbetaling) ->
                val beløpTidligereUtbetaling = simulering.hentUtbetalteBeløp(måned)!!.sum()
                val beløpNyUtbetaling = simulering.hentTotalUtbetaling(måned)!!.sum()
                val beløpSkalTilbakekreves = feilutbetaling.sum()
                require(beløpTidligereUtbetaling - beløpNyUtbetaling == beløpSkalTilbakekreves) {
                    "Forventet at beløpTidligereUtbetaling ($beløpTidligereUtbetaling) - beløpNyUtbetaling($beløpNyUtbetaling) == beløpSkalTilbakekreves($beløpSkalTilbakekreves)."
                }
                Kravgrunnlag.Grunnlagsperiode(
                    periode = måned,
                    betaltSkattForYtelsesgruppen = skatteprosent.times(BigDecimal(beløpSkalTilbakekreves)).divide(
                        BigDecimal(100.0000),
                    ).setScale(0, RoundingMode.UP).intValueExact(),
                    bruttoTidligereUtbetalt = beløpTidligereUtbetaling,
                    bruttoNyUtbetaling = beløpNyUtbetaling,
                    bruttoFeilutbetaling = beløpSkalTilbakekreves,
                    skatteProsent = skatteprosent,
                )
            },
    )
}

private fun Kravgrunnlagstatus.toDtoStatus(): String = when (this) {
    Kravgrunnlagstatus.Annullert -> "ANNU"
    Kravgrunnlagstatus.AnnullertVedOmg -> "ANOM"
    Kravgrunnlagstatus.Avsluttet -> "AVSL"
    Kravgrunnlagstatus.Ferdigbehandlet -> "BEGA"
    Kravgrunnlagstatus.Endret -> "ENDR"
    Kravgrunnlagstatus.Feil -> "FEIL"
    Kravgrunnlagstatus.Manuell -> "MANU"
    Kravgrunnlagstatus.Nytt -> "NY"
    Kravgrunnlagstatus.Sperret -> "SPER"
}

private data class KravgrunnlagData(
    val sakstype: Sakstype,
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
          select DISTINCT ON(u.id) s.saksnummer, s.type, u.id, u.simulering, u.opprettet from utbetaling u
          join sak s on u.sakid = s.id
          JOIN LATERAL jsonb_array_elements_text(simulering->'periodeList') pl ON TRUE
          JOIN LATERAL jsonb_array_elements_text((pl::jsonb)->'utbetaling') ut ON TRUE
          WHERE (ut::jsonb)->>'feilkonto' = 'true'
          AND u.id not in (SELECT extracted_value FROM xml_values where extracted_value is not null)
          ORDER BY u.id, u.opprettet
          )
          SELECT saksnummer, type, id, simulering 
          FROM distinct_utbetaling
          ORDER BY opprettet;
        """.trimIndent().hentListe(
            params = emptyMap(),
            session = it,
        ) {
            KravgrunnlagData(
                sakstype = Sakstype.from(it.string("type")),
                saksnummer = Saksnummer(it.long("saksnummer")),
                utbetalingId = it.uuid30("id"),
                simulering = it.string("simulering").deserializeSimulering(),
            )
        }
    }
}
