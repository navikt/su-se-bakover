package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.client.oppdrag.OppdragslinjeDefaults
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.toOppdragDate
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingskjøreplan
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.OppdragForKonsistensavstemming

internal class KonsistensavstemmingRequestBuilder(
    private val avstemming: Avstemming.Konsistensavstemming.Ny,
) {
    private val aksjonsdata = Aksjonsdata.Konsistensavstemming(
        avleverendeAvstemmingId = avstemming.id.toString(),
        tidspunktAvstemmingTom = avstemming.opprettetTilOgMed
            .toOppdragTimestamp(),
    )

    private fun lagData(): List<KonsistensavstemmingData> {
        return avstemming.løpendeUtbetalinger
            .map {
                KonsistensavstemmingData(
                    aksjonsdata = aksjonsdata,
                    oppdragsdataListe = listOf(it.toOppdragdata()),
                )
            }
    }

    private fun lagTotaldata(): KonsistensavstemmingData {
        return KonsistensavstemmingData(
            aksjonsdata = aksjonsdata,
            oppdragsdataListe = null,
            totaldata = Totaldata(
                totalAntall = avstemming.løpendeUtbetalinger.count(),
                totalBelop = avstemming.løpendeUtbetalinger.flatMap { it.utbetalingslinjer }.sumOf { it.beløp }
                    .toBigDecimal(),
                fortegn = Fortegn.TILLEGG,
            ),
        )
    }

    fun startXml(): String {
        return XmlMapper.writeValueAsString(
            SendAsynkronKonsistensavstemmingsdata(request = SendKonsistensavstemmingRequest(aksjonsdata.start())),
        )
    }

    fun dataXml(): List<String> {
        return lagData().map {
            XmlMapper.writeValueAsString(
                SendAsynkronKonsistensavstemmingsdata(request = SendKonsistensavstemmingRequest(it)),
            )
        }
    }

    fun totaldataXml(): String {
        return XmlMapper.writeValueAsString(
            SendAsynkronKonsistensavstemmingsdata(request = SendKonsistensavstemmingRequest(lagTotaldata())),
        )
    }

    fun avsluttXml(): String {
        return XmlMapper.writeValueAsString(
            SendAsynkronKonsistensavstemmingsdata(request = SendKonsistensavstemmingRequest(aksjonsdata.avslutt())),
        )
    }
}

internal fun OppdragForKonsistensavstemming.toOppdragdata(): KonsistensavstemmingData.Oppdragsdata {
    return KonsistensavstemmingData.Oppdragsdata(
        fagomradeKode = OppdragDefaults.KODE_FAGOMRÅDE,
        fagsystemId = saksnummer.toString(),
        utbetalingsfrekvens = OppdragDefaults.utbetalingsfrekvens.value,
        oppdragGjelderId = fnr.toString(),
        oppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom,
        saksbehandlerId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
        oppdragsenhetListe = listOf(OppdragDefaults.oppdragsenhet).map {
            KonsistensavstemmingData.Enhet(
                enhetType = it.typeEnhet,
                enhet = it.enhet,
                enhetFom = it.datoEnhetFom,
            )
        },
        oppdragslinjeListe = utbetalingslinjer.map {
            KonsistensavstemmingData.Oppdragslinje(
                delytelseId = it.id.toString(),
                klassifikasjonKode = OppdragslinjeDefaults.KODE_KLASSIFIK,
                vedtakPeriode = KonsistensavstemmingData.VedtakPeriode(
                    fom = it.fraOgMed.toOppdragDate(),
                    tom = it.tilOgMed.toOppdragDate(),
                ),
                sats = it.beløp.toBigDecimal(),
                satstypeKode = OppdragslinjeDefaults.typeSats.value,
                fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg.toString(),
                brukKjoreplan = when (it.kjøreplan) {
                    Utbetalingskjøreplan.JA -> "J"
                    Utbetalingskjøreplan.NEI -> "N"
                },
                utbetalesTilId = fnr.toString(),
                attestantListe = it.attestanter.map { attestant ->
                    KonsistensavstemmingData.Attestant(attestantId = attestant.navIdent)
                },
            )
        },
    )
}
