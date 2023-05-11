package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

interface ResendStatistikkhendelserService {

    fun resendIverksattSøknadsbehandling(fraOgMedDato: LocalDate)
    fun resendStatistikkForVedtak(vedtakId: UUID, requiredType: KClass<*>? = null): Either<Unit, Unit>
}
