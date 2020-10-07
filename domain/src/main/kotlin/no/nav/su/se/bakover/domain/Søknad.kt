package no.nav.su.se.bakover.domain

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import java.util.UUID

data class Søknad(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val søknadInnhold: SøknadInnhold
)

data class AvsluttSøknadsBehandlingBody(
    val sakId: UUID,
    val søknadId: UUID,
    val avsluttSøkndsBehandlingBegrunnelse: AvsluttSøkndsBehandlingBegrunnelse
) {
    fun valid() = avsluttSøkndsBehandlingBegrunnelse == AvsluttSøkndsBehandlingBegrunnelse.AvvistSøktForTidlig ||
        avsluttSøkndsBehandlingBegrunnelse == AvsluttSøkndsBehandlingBegrunnelse.Bortfalt ||
        avsluttSøkndsBehandlingBegrunnelse == AvsluttSøkndsBehandlingBegrunnelse.Trukket
}

enum class AvsluttSøkndsBehandlingBegrunnelse {
    Trukket,
    Bortfalt,
    AvvistSøktForTidlig;

    companion object {
        fun isValid(s: String) =
            runBlocking {
                Either.catch { valueOf(s) }
                    .isRight()
            }
    }
}
