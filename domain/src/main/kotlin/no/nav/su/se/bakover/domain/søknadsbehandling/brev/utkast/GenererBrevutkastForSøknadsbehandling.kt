package no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast

import arrow.core.Either
import arrow.core.left
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknadsbehandling.KanGenerereBrev
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import satser.domain.SatsFactory

fun genererBrevutkastForSøknadsbehandling(
    command: BrevutkastForSøknadsbehandlingCommand,
    hentSøknadsbehandling: (SøknadsbehandlingId) -> Søknadsbehandling?,
    lagDokument: (GenererDokumentCommand) -> Either<KunneIkkeLageDokument, Dokument.UtenMetadata>,
    satsFactory: SatsFactory,
): Either<KunneIkkeGenerereBrevutkastForSøknadsbehandling, Pair<PdfA, Fnr>> {
    val søknadsbehandling = (
        hentSøknadsbehandling(command.søknadsbehandlingId)
            ?: throw IllegalArgumentException("Fant ikke søknadsbehandling med id ${command.søknadsbehandlingId}")
        ).let {
        it as? KanGenerereBrev
            ?: return KunneIkkeGenerereBrevutkastForSøknadsbehandling.UgyldigTilstand(it::class).left()
    }
    return when (command) {
        is BrevutkastForSøknadsbehandlingCommand.ForSaksbehandler -> søknadsbehandling.lagBrevutkastCommandForSaksbehandler(
            satsFactory = satsFactory,
            utførtAv = command.utførtAv,
            fritekst = command.fritekst,

        )

        is BrevutkastForSøknadsbehandlingCommand.ForAttestant -> søknadsbehandling.lagBrevutkastCommandForAttestant(
            satsFactory = satsFactory,
            utførtAv = command.utførtAv,
            fritekst = command.fritekst,
        )
    }.let {
        // Merk at siden vi ikke skal lagre dokumentet, så trenger vi ikke å legge til metadata.
        lagDokument(it)
            .mapLeft { KunneIkkeGenerereBrevutkastForSøknadsbehandling.UnderliggendeFeil(it) }
            .map { it.generertDokument to søknadsbehandling.fnr }
    }
}
