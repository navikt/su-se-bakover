package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.getDistinkteParagrafer
import no.nav.su.se.bakover.domain.brev.FormueForBrev
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.brev.tilFormueForBrev

data class AvslagSøknadsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val avslagsgrunner: List<Avslagsgrunn>,
    val harEktefelle: Boolean,
    val halvGrunnbeløp: Int,
    val beregningsperioder: List<Beregningsperiode>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String,
    val forventetInntektStørreEnn0: Boolean,
    val formueVerdier: FormueForBrev?,
    val satsoversikt: Satsoversikt?,
    override val sakstype: Sakstype,
) : PdfInnhold() {
    @Suppress("unused")
    @JsonInclude
    val harFlereAvslagsgrunner: Boolean = avslagsgrunner.size > 1

    @Suppress("unused")
    @JsonInclude
    val avslagsparagrafer: List<Int> = avslagsgrunner.getDistinkteParagrafer()

    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.AvslagsVedtak

    companion object {
        fun fromBrevCommand(
            command: IverksettSøknadsbehandlingDokumentCommand.Avslag,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String,
        ): AvslagSøknadsbehandlingPdfInnhold {
            return AvslagSøknadsbehandlingPdfInnhold(
                personalia = personalia,
                avslagsgrunner = command.avslag.avslagsgrunner,
                harEktefelle = command.avslag.harEktefelle,
                halvGrunnbeløp = command.avslag.halvtGrunnbeløpPerÅr,
                beregningsperioder = command.avslag.beregning?.let { LagBrevinnholdForBeregning(it).brevInnhold }
                    ?: emptyList(),
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = command.fritekst,
                forventetInntektStørreEnn0 = command.forventetInntektStørreEnn0,
                formueVerdier = command.avslag.formuegrunnlag?.tilFormueForBrev(),
                satsoversikt = command.satsoversikt,
                sakstype = command.sakstype,
            )
        }
    }
}
