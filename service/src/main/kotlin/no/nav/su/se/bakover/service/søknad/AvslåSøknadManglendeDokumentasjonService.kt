package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.KunneIkkeAvslåSøknad

interface AvslåSøknadManglendeDokumentasjonService {
    fun avslå(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeAvslåSøknad, Sak>
    fun genererBrevForhåndsvisning(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeAvslåSøknad, Pair<Fnr, ByteArray>>
}
