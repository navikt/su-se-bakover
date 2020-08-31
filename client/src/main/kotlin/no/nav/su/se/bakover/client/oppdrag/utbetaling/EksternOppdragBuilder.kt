package no.nav.su.se.bakover.client.oppdrag.utbetaling

import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class EksternOppdragBuilder(
    private val utbetaling: Utbetaling,
    private val oppdragGjelder: String
) {
    private companion object {
        private const val FAGOMRÅDE = "SUUFORE"
        private const val KLASSEKODE = "SUUFORE"
        private const val SAKSBEHANDLER = "SU"
        private val yyyyMMdd = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    internal val oppdragRequest = Oppdrag().apply {
        kodeFagomraade = FAGOMRÅDE
        kodeEndring =
            "NY" // TODO: Så lenge vi ikke gjør en faktisk utbetaling, vil denne være NY uavhengig hvor mange simuleringer vi gjør.
        utbetFrekvens = "MND"
        fagsystemId = utbetaling.oppdragId.toString()
        oppdragGjelderId = oppdragGjelder
        saksbehId = SAKSBEHANDLER
        datoOppdragGjelderFom = LocalDate.EPOCH.format(yyyyMMdd)
        enhet.add(
            Enhet().apply {
                enhet = "8020"
                typeEnhet = "BOS"
                datoEnhetFom = LocalDate.EPOCH.format(yyyyMMdd)
            }
        )
    }
}
