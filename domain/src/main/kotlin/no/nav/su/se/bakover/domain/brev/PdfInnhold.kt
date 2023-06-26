package no.nav.su.se.bakover.domain.brev

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn.Companion.getDistinkteParagrafer
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevPeriode
import no.nav.su.se.bakover.domain.brev.beregning.BrevTilbakekrevingInfo
import no.nav.su.se.bakover.domain.sak.Sakstype

/**
 * TODO jah: Dette er en ren JsonDto som sendes serialisert til su-pdfgen. Den bør bo under client-modulen eller en tilsvarende infrastruktur-modul.
 */
abstract class PdfInnhold {
    fun toJson(): String = serialize(this)

    @get:JsonIgnore
    abstract val pdfTemplate: PdfTemplateMedDokumentNavn
    // TODO CHM 05.05.2021: Se på å samle mer av det som er felles for brevinnholdene, f.eks. personalia

    // TODO ØH 21.06.2022: Denne bør være abstract på sikt, og settes for alle brev eksplisitt
    @get:JsonIgnore
    open val sakstype: Sakstype = Sakstype.UFØRE

    @JsonProperty
    fun erAldersbrev(): Boolean = this.sakstype == Sakstype.ALDER

    data class AvslagsPdfInnhold(
        val personalia: Personalia,
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
    }

    data class InnvilgetVedtak(
        val personalia: Personalia,
        val fradato: String,
        val tildato: String,
        val forventetInntektStørreEnn0: Boolean,
        val harEktefelle: Boolean,
        val beregningsperioder: List<Beregningsperiode>,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val fritekst: String,
        val satsoversikt: Satsoversikt,
        override val sakstype: Sakstype,
    ) : PdfInnhold() {
        override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.InnvilgetVedtak

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()

        @Suppress("unused")
        @JsonInclude
        val harAvkorting: Boolean = beregningsperioder.harAvkorting()
    }

    data class Opphørsvedtak(
        val personalia: Personalia,
        val opphørsgrunner: List<Opphørsgrunn>,
        val avslagsparagrafer: List<Int>,
        val harEktefelle: Boolean,
        val beregningsperioder: List<Beregningsperiode>,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val fritekst: String,
        val forventetInntektStørreEnn0: Boolean,
        val halvGrunnbeløp: Int?,
        val opphørsperiode: BrevPeriode,
        val avkortingsBeløp: Int?,
        val satsoversikt: Satsoversikt,
    ) : PdfInnhold() {
        override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.Opphør.Opphørsvedtak

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()

        @Suppress("unused")
        @JsonInclude
        val harAvkorting: Boolean = beregningsperioder.harAvkorting()
    }

    data class Personalia(
        val dato: String,
        val fødselsnummer: Fnr,
        val fornavn: String,
        val etternavn: String,
        val saksnummer: Long,
    )

    data class RevurderingAvInntekt(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val beregningsperioder: List<Beregningsperiode>,
        val fritekst: String,
        val harEktefelle: Boolean,
        val forventetInntektStørreEnn0: Boolean,
        val satsoversikt: Satsoversikt,
    ) : PdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.Revurdering.Inntekt

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()

        @Suppress("unused")
        @JsonInclude
        val harAvkorting: Boolean = beregningsperioder.harAvkorting()
    }

    data class RevurderingMedTilbakekrevingAvPenger(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val attestantNavn: String,
        val beregningsperioder: List<Beregningsperiode>,
        val fritekst: String,
        val harEktefelle: Boolean,
        val forventetInntektStørreEnn0: Boolean,
        val tilbakekreving: List<BrevTilbakekrevingInfo>,
        val periodeStart: String,
        val periodeSlutt: String,
        val satsoversikt: Satsoversikt,
    ) : PdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.Revurdering.MedTilbakekreving

        @Suppress("unused")
        @JsonInclude
        val harFradrag: Boolean = beregningsperioder.harFradrag()

        @Suppress("unused")
        @JsonInclude
        val harAvkorting: Boolean = beregningsperioder.harAvkorting()
    }

    data class Forhåndsvarsel(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val fritekst: String,
    ) : PdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.Forhåndsvarsel
    }

    data class ForhåndsvarselTilbakekreving(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val fritekst: String,
        val bruttoTilbakekreving: String,
        val tilbakekreving: List<BrevTilbakekrevingInfo>,
        val periodeStart: String,
        val periodeSlutt: String,
        val dato: String,
    ) : PdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.ForhåndsvarselTilbakekreving
    }

    /**
     * Brev for når en revurdering er forhåndsvarslet
     * hvis revurderingen ikke er forhåndsvarslet, er det ikke noe brev.
     */
    data class AvsluttRevurdering(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val fritekst: String?,
    ) : PdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.Revurdering.AvsluttRevurdering
    }

    data class InnkallingTilKontrollsamtale(
        val personalia: Personalia,
    ) : PdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.InnkallingTilKontrollsamtale
    }

    data class PåminnelseNyStønadsperiode(
        val personalia: Personalia,
        val utløpsdato: String,
        val halvtGrunnbeløp: Int,
    ) : PdfInnhold() {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode
    }

    sealed class Klage : PdfInnhold() {

        data class Oppretthold(
            val personalia: Personalia,
            val saksbehandlerNavn: String,
            val attestantNavn: String?,
            val fritekst: String,
            val klageDato: String,
            val vedtakDato: String,
            val saksnummer: Long,
        ) : Klage() {
            override val pdfTemplate = PdfTemplateMedDokumentNavn.Klage.Oppretthold
        }

        data class Avvist(
            val personalia: Personalia,
            val saksbehandlerNavn: String,
            val attestantNavn: String?,
            val fritekst: String,
            val saksnummer: Long,
        ) : Klage() {
            override val pdfTemplate = PdfTemplateMedDokumentNavn.Klage.Avvist
        }
    }

    data class Fritekst(
        val personalia: Personalia,
        val saksbehandlerNavn: String,
        val tittel: String,
        val fritekst: String,
    ) : PdfInnhold() {
        override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.Fritekst(tittel)
    }
}

fun List<Beregningsperiode>.harFradrag() =
    this.any {
        it.fradrag.bruker.filterNot { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" }
            .isNotEmpty() || it.fradrag.eps.fradrag.isNotEmpty()
    }

fun List<Beregningsperiode>.harAvkorting() =
    this.any { it.fradrag.bruker.any { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" } }
