package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import java.time.LocalDate
import java.util.UUID

interface ResendStatistikkhendelserService {

    fun resendIverksattSøknadsbehandling(fraOgMedDato: LocalDate)
    fun resendStatistikkForVedtak(vedtakId: UUID): Either<Unit, Unit>
}
