package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import no.nav.su.se.bakover.client.oppdrag.MqClient
import no.nav.su.se.bakover.client.oppdrag.MqClient.CouldNotPublish
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Attestant180
import no.trygdeetaten.skjema.oppdrag.Avstemming115
import no.trygdeetaten.skjema.oppdrag.Oppdrag110
import no.trygdeetaten.skjema.oppdrag.OppdragsEnhet120
import no.trygdeetaten.skjema.oppdrag.OppdragsLinje150
import no.trygdeetaten.skjema.oppdrag.TfradragTillegg
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory
import no.trygdeetaten.skjema.oppdrag.Oppdrag as ExternalOppdrag

class UtbetalingClient(
    private val clock: Clock = Clock.systemUTC(),
    private val mqClient: MqClient
) {

    private companion object {
        private val log = LoggerFactory.getLogger(UtbetalingClient::class.java)
        private const val FAGOMRÅDE = "SUUFORE"
        private const val KLASSEKODE = "SUUFORE"
        private const val SAKSBEHANDLER = "SU"

        private val datostempel = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault())
        private val tidsstempel = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
            .withZone(ZoneId.systemDefault())
        private val datatypeFactory = DatatypeFactory.newInstance()
        private fun LocalDate.asXmlGregorianCalendar() =
            datatypeFactory.newXMLGregorianCalendar(GregorianCalendar.from(this.atStartOfDay(ZoneId.systemDefault())))
    }

    fun sendUtbetaling(
        utbetaling: Utbetaling,
        oppdragGjelder: String
    ) : Either<CouldNotPublish, Unit> {
        val xml = OppdragXml.marshal(utbetaling.toExternal(oppdragGjelder))
        return mqClient.publish(xml)
    }

    private fun Utbetaling.toExternal(oppdragGjelder: String) = ExternalOppdrag().also {
        it.oppdrag110 = Oppdrag110().also {
            it.kodeFagomraade = FAGOMRÅDE
            it.kodeEndring = "NY"
            it.kodeAksjon = "1" // TODO Skal denne være med?
            it.utbetFrekvens = "MND"
            it.fagsystemId = this.oppdragId.toString()
            it.oppdragGjelderId = oppdragGjelder
            it.saksbehId = SAKSBEHANDLER
            it.datoOppdragGjelderFom = LocalDate.EPOCH.asXmlGregorianCalendar()
            it.oppdragsEnhet120.add(
                OppdragsEnhet120().apply {
                    enhet = "8020"
                    typeEnhet = "BOS"
                    datoEnhetFom = LocalDate.EPOCH.asXmlGregorianCalendar()
                }
            )
            it.avstemming115 = Avstemming115().apply {
                nokkelAvstemming = "TODO" // TODO hent verdi
                tidspktMelding = tidsstempel.format(now(clock))
                kodeKomponent = KLASSEKODE // TODO. Verifiser hva denne skal være
            }
            utbetalingslinjer.forEach { utbetalingslinje ->
                it.oppdragsLinje150.add(
                    OppdragsLinje150().also {
                        it.utbetalesTilId = oppdragGjelder
                        it.delytelseId = utbetalingslinje.id.toString()
                        it.refDelytelseId = utbetalingslinje.forrigeUtbetalingslinjeId?.toString()
                        it.kodeEndringLinje = "NY"
                        it.kodeKlassifik = KLASSEKODE
                        it.datoVedtakFom = utbetalingslinje.fom.asXmlGregorianCalendar()
                        it.datoVedtakTom = utbetalingslinje.tom.asXmlGregorianCalendar()
                        it.sats = utbetalingslinje.beløp.toBigDecimal()
                        it.fradragTillegg = TfradragTillegg.T
                        it.typeSats = "MND"
                        it.saksbehId = SAKSBEHANDLER
                        it.brukKjoreplan = "N"
                        it.attestant180.add(
                            Attestant180().apply {
                                attestantId = SAKSBEHANDLER
                            }
                        )
                    }
                )
            }
        }
    }
}
