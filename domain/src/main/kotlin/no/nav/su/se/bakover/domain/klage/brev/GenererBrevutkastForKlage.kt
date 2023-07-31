package no.nav.su.se.bakover.domain.klage.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.klage.KanGenerereBrevutkast
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import java.time.LocalDate
import java.util.UUID

/**
 * Funksjonell service (tilstandsløs) for å generere brevutkast for klage.
 * For å holde domenelogikken i domenelaget, og ikke i servicelaget.
 */
fun genererBrevutkastForKlage(
    klageId: UUID,
    ident: NavIdentBruker,
    hentKlage: (UUID) -> Klage?,
    hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
    genererPdf: (KlageDokumentCommand) -> Either<KunneIkkeLageDokument, PdfA>,
): Either<KunneIkkeLageBrevutkast, PdfA> {
    val klage: KanGenerereBrevutkast = when (val k = hentKlage(klageId)) {
        null -> return KunneIkkeLageBrevutkast.FantIkkeKlage.left()
        else -> {
            (k as? KanGenerereBrevutkast) ?: return KunneIkkeLageBrevutkast.FeilVedBrevRequest(
                KunneIkkeLageBrevKommandoForKlage.UgyldigTilstand(fra = k::class),
            ).left()
        }
    }
    return klage.lagBrevRequest(
        utførtAv = ident,
        hentVedtaksbrevDato = hentVedtaksbrevDato,
    ).mapLeft {
        KunneIkkeLageBrevutkast.FeilVedBrevRequest(it)
    }.flatMap {
        genererPdf(it).mapLeft {
            KunneIkkeLageBrevutkast.KunneIkkeGenererePdf(it)
        }
    }
}
