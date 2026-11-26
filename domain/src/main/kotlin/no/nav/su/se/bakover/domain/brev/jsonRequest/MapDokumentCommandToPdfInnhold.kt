package no.nav.su.se.bakover.domain.brev.jsonRequest

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.KunneIkkeHenteNavnForNavIdent
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import java.time.Clock
import java.time.LocalDate

/**
 * Innholdet en brevkommando gir, med personalia og navn hentet.
 * Samme vei som [dokument.domain.generer.lagBrev] bruker, uten PDF-generering.
 */
fun BrevmalCommand.tilPdfInnhold(
    clock: Clock,
    hentPerson: (Fnr) -> Either<KunneIkkeHentePerson, Person>,
    hentNavnForIdent: (NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
): Either<FeilVedHentingAvInformasjon, PdfInnhold> =
    hentFelles(clock, hentPerson, hentNavnForIdent).map { lagInnhold(it) }

/**
 * Effektene i [dokument.domain.generer.lagBrev]: henter personalia og navn på saksbehandler og attestant.
 * Manglende saksbehandler gir `"-"`. Manglende attestant gir `null`, og malen bestemmer hva som vises.
 */
fun BrevmalCommand.hentFelles(
    clock: Clock,
    hentPerson: (Fnr) -> Either<KunneIkkeHentePerson, Person>,
    hentNavnForIdent: (NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
): Either<FeilVedHentingAvInformasjon, Felles> = either {
    Felles(
        sakstype = sakstype,
        personalia = hentPersonalia(this@hentFelles, hentPerson, clock).bind(),
        saksbehandlerNavn = hentNavn(saksbehandler, hentNavnForIdent).bind(),
        attestantNavn = attestant?.let { hentNavn(it, hentNavnForIdent).bind() },
        idag = LocalDate.now(clock),
    )
}

private fun hentNavn(
    ident: NavIdentBruker?,
    hentNavnForIdent: (NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
): Either<FeilVedHentingAvInformasjon.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String> = when {
    ident == null -> "-".right()
    ident.navIdent == SU_SE_BAKOVER_CONSUMER_ID -> SU_SE_BAKOVER_CONSUMER_ID.right()
    else -> hentNavnForIdent(ident).mapLeft {
        FeilVedHentingAvInformasjon.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(it)
    }
}

private fun hentPersonalia(
    command: BrevmalCommand,
    hentPerson: (Fnr) -> Either<KunneIkkeHentePerson, Person>,
    clock: Clock,
): Either<FeilVedHentingAvInformasjon.KunneIkkeHentePerson, PersonaliaPdfInnhold> =
    hentPerson(command.fødselsnummer).mapLeft {
        FeilVedHentingAvInformasjon.KunneIkkeHentePerson(it)
    }.map {
        PersonaliaPdfInnhold.lagPersonalia(
            fødselsnummer = command.fødselsnummer,
            saksnummer = command.saksnummer,
            fornavn = it.navn.fornavn,
            etternavn = it.navn.etternavn,
            clock = clock,
        )
    }
