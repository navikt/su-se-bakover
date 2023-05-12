package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeGenerereSkattedokument
import no.nav.su.se.bakover.domain.vedtak.Vedtak

interface SkattDokumentService {
    fun generer(vedtak: Vedtak): Either<KunneIkkeGenerereSkattedokument, Skattedokument>
}
