package økonomi.infrastructure.kvittering.consumer.lokal

import org.slf4j.LoggerFactory
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringConsumer

/**
 * Only to be used when running locally.
 * Runs a regular job, looking for utbetalinger without kvittering and `ferdigstiller utbetaling`
 */
class LokalKvitteringService(
    private val utbetalingRepo: UtbetalingRepo,
    private val utbetalingKvitteringConsumer: UtbetalingKvitteringConsumer,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun run() {
        utbetalingRepo.hentUkvitterteUtbetalinger().forEach {
            log.info("Lokal jobb: Persisterer kvittering og ferdigstiller innvilget behandling for utbetaling ${it.id}")
            utbetalingKvitteringConsumer.onMessage(kvitteringXml(it))
        }
    }

    //language=XML
    private fun kvitteringXml(utbetaling: Utbetaling) =
        """
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
          <fagsystemId>35413bd5-f66a-44d8-b7e9-1006d5</fagsystemId>
          <utbetFrekvens>MND</utbetFrekvens>
          <oppdragGjelderId>18127621833</oppdragGjelderId>
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
             <utbetalesTilId>18127621833</utbetalesTilId>
             <attestant-180>
                <attestantId>A123456</attestantId>
             </attestant-180>
             <henvisning>${utbetaling.id}</henvisning>
             <ukjentFeltBørIgnorereres>ukjent</ukjentFeltBørIgnorereres>
          </oppdrags-linje-150>
       </oppdrag-110>
    </Oppdrag>
        """.trimIndent()
}
