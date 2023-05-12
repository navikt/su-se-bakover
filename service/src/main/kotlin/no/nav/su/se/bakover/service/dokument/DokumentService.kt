package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeGenerereSkattedokument
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak

interface DokumentService {
    fun lagSkattedokumentFor(vedtak: Stønadsvedtak): Either<KunneIkkeGenerereSkattedokument, Skattedokument>
}
