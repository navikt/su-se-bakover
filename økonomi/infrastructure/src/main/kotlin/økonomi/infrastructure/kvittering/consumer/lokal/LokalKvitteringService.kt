package økonomi.infrastructure.kvittering.consumer.lokal

import no.nav.su.se.bakover.common.infrastructure.correlation.getOrCreateCorrelationIdFromThreadLocal
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import org.slf4j.LoggerFactory
import økonomi.application.kvittering.FerdigstillVedtakEtterMottattKvitteringKonsument
import økonomi.application.kvittering.KnyttKvitteringTilSakOgUtbetalingKonsument
import økonomi.application.kvittering.RåKvitteringService
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingRepo

/**
 * Only to be used when running locally.
 * Runs a regular job, looking for utbetalinger without kvittering and `ferdigstiller utbetaling`
 */
@Suppress("HttpUrlsUsage")
class LokalKvitteringService(
    private val utbetalingRepo: UtbetalingRepo,
    private val råKvitteringService: RåKvitteringService,
    private val knyttKvitteringTilSakOgUtbetalingService: KnyttKvitteringTilSakOgUtbetalingKonsument,
    private val ferdigstillVedtakEtterMottattKvitteringKonsument: FerdigstillVedtakEtterMottattKvitteringKonsument,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun run() {
        val correlationId = getOrCreateCorrelationIdFromThreadLocal()
        val hentUkvitterteUtbetalinger = utbetalingRepo.hentUkvitterteUtbetalinger()
        hentUkvitterteUtbetalinger.forEach {
            log.info("Lokal jobb: Persisterer kvittering og ferdigstiller innvilget behandling for utbetaling ${it.id}")
            råKvitteringService.lagreRåKvitteringshendelse(
                kvitteringXml(it),
                JMSHendelseMetadata.fromCorrelationId(
                    correlationId,
                ),
            )
            knyttKvitteringTilSakOgUtbetalingService.knyttKvitteringerTilSakOgUtbetaling(
                correlationId = correlationId,
            )
            ferdigstillVedtakEtterMottattKvitteringKonsument.ferdigstillVedtakEtterMottattKvittering()
        }
    }

    private fun kvitteringXml(utbetaling: Utbetaling): String {
        //language=XML
        return """
    <?xml version="1.0" encoding="UTF-8"?>
        <oppdrag xmlns="http://www.trygdeetaten.no/skjema/oppdrag">
           <mmel>
              <systemId>231-OPPD</systemId>
              <alvorlighetsgrad>00</alvorlighetsgrad>
           </mmel>
           <oppdrag-110>
              <kodeAksjon>1</kodeAksjon>
              <kodeEndring>NY</kodeEndring>
              <kodeFagomraade>SUUFORE</kodeFagomraade>
              <fagsystemId>${utbetaling.saksnummer}</fagsystemId>
              <utbetFrekvens>MND</utbetFrekvens>
              <oppdragGjelderId>${utbetaling.fnr}</oppdragGjelderId>
              <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
              <saksbehId>SU</saksbehId>
              <avstemming-115>
                 <kodeKomponent>SU</kodeKomponent>
                 <nokkelAvstemming>${utbetaling.avstemmingsnøkkel}</nokkelAvstemming>
                 <tidspktMelding>${utbetaling.avstemmingsnøkkel.opprettet}</tidspktMelding>
              </avstemming-115>
              <oppdrags-enhet-120>
                 <typeEnhet>BOS</typeEnhet>
                 <enhet>8020</enhet>
                 <datoEnhetFom>1970-01-01</datoEnhetFom>
              </oppdrags-enhet-120>
              <oppdrags-linje-150>
                 <kodeEndringLinje>NY</kodeEndringLinje>
                 <delytelseId>4fad33a7-9a7d-4732-9d3f-b9d0fc</delytelseId>
                 <kodeKlassifik>SUUFORE</kodeKlassifik>
                 <datoVedtakFom>2020-01-01</datoVedtakFom>
                 <datoVedtakTom>2020-12-31</datoVedtakTom>
                 <sats>20637</sats>
                 <fradragTillegg>T</fradragTillegg>
                 <typeSats>MND</typeSats>
                 <brukKjoreplan>N</brukKjoreplan>
                 <saksbehId>SU</saksbehId>
                 <utbetalesTilId>${utbetaling.fnr}</utbetalesTilId>
                 <attestant-180>
                    <attestantId>A123456</attestantId>
                 </attestant-180>
                 <henvisning>${utbetaling.id}</henvisning>
                 <ukjentFeltBørIgnorereres>ukjent</ukjentFeltBørIgnorereres>
              </oppdrags-linje-150>
           </oppdrag-110>
        </oppdrag>
        """.trimIndent()
    }
}
