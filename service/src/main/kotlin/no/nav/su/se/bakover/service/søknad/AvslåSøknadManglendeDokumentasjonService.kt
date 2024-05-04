package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.KunneIkkeAvslåSøknad

interface AvslåSøknadManglendeDokumentasjonService {
    fun avslå(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeAvslåSøknad, Sak>
    fun genererBrevForhåndsvisning(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeLageDokument, Pair<Fnr, PdfA>>
}
