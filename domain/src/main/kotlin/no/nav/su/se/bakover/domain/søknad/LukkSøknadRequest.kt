package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.bruker.NavIdentBruker
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

sealed class LukkSøknadRequest {
    abstract val søknadId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler

    companion object {
        fun Søknad.Journalført.MedOppgave.IkkeLukket.lukk(request: LukkSøknadRequest, lukketTidspunkt: Tidspunkt): Søknad.Journalført.MedOppgave.Lukket {
            return lukk(
                lukketAv = request.saksbehandler,
                type = when (request) {
                    is MedBrev.TrekkSøknad -> Søknad.Journalført.MedOppgave.Lukket.LukketType.TRUKKET
                    is MedBrev.AvvistSøknad -> Søknad.Journalført.MedOppgave.Lukket.LukketType.AVVIST
                    is UtenBrev.BortfaltSøknad -> Søknad.Journalført.MedOppgave.Lukket.LukketType.BORTFALT
                    is UtenBrev.AvvistSøknad -> Søknad.Journalført.MedOppgave.Lukket.LukketType.AVVIST
                },
                lukketTidspunkt = lukketTidspunkt,
            )
        }
    }

    sealed class MedBrev : LukkSøknadRequest() {

        data class TrekkSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            val trukketDato: LocalDate
        ) : MedBrev() {
            fun erDatoGyldig(ikkeFør: LocalDate, clock: Clock): Boolean {
                return !trukketDato.isBefore(ikkeFør) && !trukketDato.isAfter(LocalDate.now(clock))
            }
        }

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            val brevConfig: BrevConfig
        ) : MedBrev()
    }

    sealed class UtenBrev : LukkSøknadRequest() {
        data class BortfaltSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler
        ) : UtenBrev()

        data class AvvistSøknad(
            override val søknadId: UUID,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
        ) : UtenBrev()
    }
}
