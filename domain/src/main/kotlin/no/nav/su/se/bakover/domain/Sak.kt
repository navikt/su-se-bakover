package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.Clock
import java.util.UUID

data class Saksnummer(@JsonValue val nummer: Long) {
    override fun toString() = nummer.toString()

    init {
        // Since we have a public ctor and json-deserialization directly into the domain object
        if (isInvalid(nummer)) throw IllegalArgumentException(UgyldigSaksnummer.toString())
    }

    companion object {
        fun tryParse(saksnummer: String): Either<UgyldigSaksnummer, Saksnummer> {
            return saksnummer.toLongOrNull()?.let {
                tryParse(it)
            } ?: UgyldigSaksnummer.left()
        }

        private fun tryParse(saksnummer: Long): Either<UgyldigSaksnummer, Saksnummer> {
            if (isInvalid(saksnummer)) return UgyldigSaksnummer.left()
            return Saksnummer(saksnummer).right()
        }

        private fun isInvalid(saksnummer: Long) = saksnummer < 2021
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
    val vedtakListe: List<Vedtak> = emptyList(),
) {
    fun hentÅpneRevurderinger(): List<Revurdering> {
        return revurderinger.filterNot {
            it is IverksattRevurdering
        }
    }

    fun hentÅpneSøknadsbehandlinger(): List<Søknadsbehandling> {
        return behandlinger.filterNot {
            it is Søknadsbehandling.Iverksatt
        }
    }

    fun hentÅpneSøknader(): List<Søknad> {
        val ikkeLukkedeSøknader = søknader.filterNot {
            it is Søknad.Lukket
        }
        val søknaderMedSøknadsbehandling = behandlinger.map {
            it.søknad
        }
        return ikkeLukkedeSøknader.minus(søknaderMedSøknadsbehandling)
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
            ),
        )
    }
}
