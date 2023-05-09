package no.nav.su.se.bakover.service.statistikk

import java.time.LocalDate

interface ResendStatistikkhendelserService {

    fun resendIverksattSøknadsbehandling(fraOgMedDato: LocalDate)
}
