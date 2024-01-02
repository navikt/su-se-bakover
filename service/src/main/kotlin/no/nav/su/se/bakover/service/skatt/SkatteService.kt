package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag

interface SkatteService {
    /**
     * Ikke i bruk. TODO jah: Slett?
     */
    fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Skattegrunnlag

    fun hentSamletSkattegrunnlagForÅr(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
        yearRange: YearRange,
    ): Skattegrunnlag

    /**
     * Brukes for frioppslag på skattemelding.
     */
    fun hentOgLagSkattePdf(request: FrioppslagSkattRequest): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, PdfA>
    fun hentLagOgJournalførSkattePdf(request: FrioppslagSkattRequest): Either<KunneIkkeGenerereSkattePdfOgJournalføre, PdfA>
}
