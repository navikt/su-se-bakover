package no.nav.su.se.bakover.domain.brev.jsonRequest

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselTilbakekrevingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.GenererDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.InnkallingTilKontrollsamtaleDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.søknad.lukk.avvist.tilAvvistSøknadPdfInnhold
import no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket.TrukketSøknadPdfInnhold
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Sakstype
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * TODO jah: Dette er en ren JsonDto som sendes serialisert til su-pdfgen.
 *  Den bør bo under client-modulen eller en tilsvarende infrastruktur-modul.
 */
abstract class PdfInnhold {
    fun toJson(): String = serialize(this)

    @get:JsonIgnore
    abstract val pdfTemplate: PdfTemplateMedDokumentNavn
    // TODO CHM 05.05.2021: Se på å samle mer av det som er felles for brevinnholdene, f.eks. personalia

    // TODO ØH 21.06.2022: Denne bør være abstract på sikt, og settes for alle brev eksplisitt
    open val sakstype: Sakstype = Sakstype.UFØRE

    @JsonProperty
    fun erAldersbrev(): Boolean = this.sakstype == Sakstype.ALDER

    companion object {

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

                    is ForhåndsvarselTilbakekrevingDokumentCommand -> ForhåndsvarselTilbakekrevingPdfInnhold.fromBrevCommand(
                        command = command,
                        personalia = personalia().bind(),
                        saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                        clock = clock,
                    )

                    is FritekstDokumentCommand -> FritekstPdfInnhold.fromBrevCommand(
                        command = command,
                        personalia = personalia().bind(),
                        saksbehandlerNavn = hentNavnMappedLeft(command.saksbehandler).bind(),
                    )

                    is InnkallingTilKontrollsamtaleDokumentCommand -> InnkallingTilKontrollsamtalePdfInnhold.fromBrevCommand(
                        personalia = personalia().bind(),
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

                    is IverksettRevurderingDokumentCommand.TilbakekrevingAvPenger -> RevurderingMedTilbakekrevingAvPengerPdfInnhold.fromBrevCommand(
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
                }
            }
        }
    }
}

fun GenererDokumentCommand.tilPdfInnhold(
    clock: Clock,
    hentPerson: (Fnr) -> Either<KunneIkkeHentePerson, Person>,
    hentNavnForIdent: (NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
): Either<FeilVedHentingAvInformasjon, PdfInnhold> {
    return PdfInnhold.fromBrevCommand(
        command = this,
        hentPerson = hentPerson,
        hentNavnForIdent = hentNavnForIdent,
        clock = clock,
    )
}

fun List<Beregningsperiode>.harFradrag(): Boolean {
    return this.any {
        it.fradrag.bruker.filterNot { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" }
            .isNotEmpty() || it.fradrag.eps.fradrag.isNotEmpty()
    }
}

fun List<Beregningsperiode>.harAvkorting(): Boolean {
    return this.any { it.fradrag.bruker.any { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" } }
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
