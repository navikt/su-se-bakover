package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class AvslagSøknadsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val avslagsgrunner: List<AvslagsgrunnBrev>,
    val avslagsparagrafer: List<Int>,
    val harFlereAvslagsgrunner: Boolean,
    val harEktefelle: Boolean,
    val halvGrunnbeløp: Int,
    val beregningsperioder: List<BeregningsperiodeBrev>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String,
    val forventetInntektStørreEnn0: Boolean?,
    val formueVerdier: FormueForBrev?,
    val satsoversikt: SatsoversiktBrev?,
    override val sakstype: Sakstype,
) : PdfInnhold {

    override val dokumentMal = DokumentMal.AvslagsVedtak
}

data class FormueForBrev(
    val søkersFormue: FormueVerdierForBrev,
    val epsFormue: FormueVerdierForBrev?,
    val totalt: Int,
)

data class FormueVerdierForBrev(
    val verdiSekundærBoliger: Int,
    val verdiSekundærKjøretøyer: Int,
    val pengerIBanken: Int,
    val depositumskonto: Int,
    val pengerIKontanter: Int,
    val aksjerOgVerdiPapir: Int,
    val pengerSøkerSkyldes: Int,
)

// Midlertidig duplisering av Avslagsgrunn.kt frem til vi løser modularisering av dokument/brev
enum class AvslagsgrunnBrev {
    UFØRHET,
    FLYKTNING,
    OPPHOLDSTILLATELSE,
    PERSONLIG_OPPMØTE,
    FORMUE,
    BOR_OG_OPPHOLDER_SEG_I_NORGE,
    FOR_HØY_INNTEKT,
    SU_UNDER_MINSTEGRENSE,
    UTENLANDSOPPHOLD_OVER_90_DAGER,
    INNLAGT_PÅ_INSTITUSJON,
    MANGLENDE_DOKUMENTASJON,
    SØKNAD_MANGLER_DOKUMENTASJON,
    ALDERSPENSJON_FOLKETRYGDEN,
    ALDERSPENSJON_ANDRE_NORSKE_PENSJONSORDNINGER,
    ALDERSPENSJON_UTENLANDSKE_PENSJONSORDNINGER,
    FAMILIEGJENFORENING,
}

val vedtakAvslag = { tittel: String, pdfInnhold: PdfInnhold ->
    TODO()
}
