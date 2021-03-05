package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Stønadsperiode
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.time.Clock
import java.util.UUID

// TODO ai 04.04.2021: Legg till validering av saksnummer
data class Saksnummer(@JsonValue val nummer: Long) {
    override fun toString() = nummer.toString()

    companion object {
        fun tryParse(saksnummer: String): Either<UgyldigSaksnummer, Saksnummer> =
            if (isNumeric(saksnummer)) Saksnummer(saksnummer.toLong()).right() else UgyldigSaksnummer.left()

        private fun isNumeric(saksnummer: String) = saksnummer.chars().allMatch(Character::isDigit)
    }

    object UgyldigSaksnummer
}

data class Sak(
    val id: UUID = UUID.randomUUID(),
    val saksnummer: Saksnummer,
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val fnr: Fnr,
    val søknader: List<Søknad> = emptyList(),
    val behandlinger: List<Søknadsbehandling> = emptyList(),
    val utbetalinger: List<Utbetaling>,
    val revurderinger: List<Revurdering> = emptyList(),
) {
    fun hentStønadsperioder(): List<Stønadsperiode> {
        return utbetalinger.map {
            Stønadsperiode.create(
                Periode.create(
                    fraOgMed = it.tidligsteDato(),
                    tilOgMed = it.senesteDato(),
                )
            )
        }
    }
}

data class NySak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val fnr: Fnr,
    val søknad: Søknad.Ny,
)

class SakFactory(
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock,
) {
    fun nySak(fnr: Fnr, søknadInnhold: SøknadInnhold): NySak {
        val opprettet = Tidspunkt.now(clock)
        val sakId = uuidFactory.newUUID()
        return NySak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            søknad = Søknad.Ny(
                id = uuidFactory.newUUID(),
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
            )
        )
    }
}
