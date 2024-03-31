package no.nav.su.se.bakover.domain.klage.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import behandling.klage.domain.KlageId
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.klage.KanGenerereBrevutkast
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import java.time.LocalDate

/**
 * Funksjonell service (tilstandsløs) for å generere brevutkast for klage.
 * For å holde domenelogikken i domenelaget, og ikke i servicelaget.
 */
fun genererBrevutkastForKlage(
    klageId: KlageId,
    ident: NavIdentBruker,
    hentKlage: (KlageId) -> Klage?,
    hentVedtaksbrevDato: (klageId: KlageId) -> LocalDate?,
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
