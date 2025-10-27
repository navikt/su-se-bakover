package no.nav.su.se.bakover.domain.brev.jsonRequest

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import dokument.domain.GenererDokumentCommand
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.InnkallingTilKontrollsamtaleDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.søknad.lukk.avvist.tilAvvistSøknadPdfInnhold
import no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket.TrukketSøknadPdfInnhold
import person.domain.KunneIkkeHenteNavnForNavIdent
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingPdfInnhold
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingUtenKravgrunnlagDokumentCommand
import tilbakekreving.domain.vedtaksbrev.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.vedtaksbrev.VedtaksbrevTilbakekrevingsbehandlingPdfInnhold
import java.time.Clock

fun GenererDokumentCommand.tilPdfInnhold(
    clock: Clock,
    hentPerson: (Fnr) -> Either<KunneIkkeHentePerson, Person>,
    hentNavnForIdent: (NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
): Either<FeilVedHentingAvInformasjon, PdfInnhold> {
    return fromBrevCommand(
        command = this,
        hentPerson = hentPerson,
        hentNavnForIdent = hentNavnForIdent,
        clock = clock,
    )
}

/**
 * Dersom vi skal ha en generell måte å forholde oss til [GenererDokumentCommand] på, må vi ha noe som dette.
 */
fun fromBrevCommand(
    command: GenererDokumentCommand,
    hentPerson: (Fnr) -> Either<KunneIkkeHentePerson, Person>,
    hentNavnForIdent: (NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
    clock: Clock,
): Either<FeilVedHentingAvInformasjon, PdfInnhold> {
    val hentNavnMappedLeft: (NavIdentBruker?) -> Either<FeilVedHentingAvInformasjon.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String> =
        {
            it?.let {
                hentNavnForIdent(it).mapLeft {
                    FeilVedHentingAvInformasjon.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(it)
                }
            } ?: "-".right()
        }
    val personalia = {
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
    }
    return either {
        when (command) {
            is IverksettSøknadsbehandlingDokumentCommand.Avslag -> AvslagSøknadsbehandlingPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                attestantNavn = hentNavnMappedLeft(command.attestant).bind(),
            )

            is AvsluttRevurderingDokumentCommand -> AvsluttRevurderingPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
            )

            is ForhåndsvarselDokumentCommand -> ForhåndsvarselPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
            )

            is FritekstDokumentCommand -> FritekstPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
            )

            is InnkallingTilKontrollsamtaleDokumentCommand -> InnkallingTilKontrollsamtalePdfInnhold.fromBrevCommand(
                personalia = personalia().bind(),
                sakstype = command.sakstype,
            )

            is IverksettSøknadsbehandlingDokumentCommand.Innvilgelse -> InnvilgetSøknadsbehandlingPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                attestantNavn = hentNavnMappedLeft(command.attestant).bind(),
            )

            is KlageDokumentCommand.Avvist -> KlagePdfInnhold.Avvist.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                attestantNavn = hentNavnMappedLeft(command.attestant).bind(),
            )

            is KlageDokumentCommand.Oppretthold -> KlagePdfInnhold.Oppretthold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                attestantNavn = hentNavnMappedLeft(command.attestant).bind(),
            )

            is IverksettRevurderingDokumentCommand.Opphør -> OpphørsvedtakPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                attestantNavn = hentNavnMappedLeft(command.attestant).bind(),
            )

            is PåminnelseNyStønadsperiodeDokumentCommand -> PåminnelseNyStønadsperiodePdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
            )

            is IverksettRevurderingDokumentCommand.Inntekt -> RevurderingAvInntektPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                attestantNavn = hentNavnMappedLeft(command.attestant).bind(),
            )

            is AvvistSøknadDokumentCommand -> command.tilAvvistSøknadPdfInnhold(
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
            )

            is TrukketSøknadDokumentCommand -> TrukketSøknadPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
            )

            is ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand -> ForhåndsvarselTilbakekrevingsbehandlingPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                clock = clock,
            )
            is ForhåndsvarsleTilbakekrevingsbehandlingUtenKravgrunnlagDokumentCommand -> TODO()

            is VedtaksbrevTilbakekrevingsbehandlingDokumentCommand -> VedtaksbrevTilbakekrevingsbehandlingPdfInnhold.fromBrevCommand(
                command = command,
                personalia = personalia().bind(),
                saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                attestantNavn = command.attestant?.let { hentNavnMappedLeft(it).bind() },
                clock = clock,
            )

            else -> throw IllegalStateException("Ukjent GenererDokumentCommand for sak ${command.saksnummer}. ")
        }
    }
}
