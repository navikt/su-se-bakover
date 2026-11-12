package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.AvslagSøknadsbehandlingPdfInnhold
import dokument.domain.generer.bygger.maler.AvslagsgrunnBrev
import dokument.domain.generer.bygger.maler.FormueForBrev
import dokument.domain.generer.bygger.maler.FormueVerdierForBrev
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.brev.toBrev
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.getDistinkteParagrafer
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.Formueverdier

fun IverksettSøknadsbehandlingDokumentCommand.Avslag.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
): AvslagSøknadsbehandlingPdfInnhold {
    val avslagsgrunner = avslag.avslagsgrunner
    return AvslagSøknadsbehandlingPdfInnhold(
        personalia = personalia,
        avslagsgrunner = avslagsgrunner.map { it.toBrev() },
        avslagsparagrafer = avslagsgrunner.getDistinkteParagrafer(),
        harFlereAvslagsgrunner = avslagsgrunner.size > 1,
        harEktefelle = avslag.harEktefelle,
        halvGrunnbeløp = avslag.halvtGrunnbeløpPerÅr,
        beregningsperioder = avslag.beregning?.let { LagBrevinnholdForBeregning(it).brevInnhold }
            ?: emptyList(),
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn,
        fritekst = fritekst,
        forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
        formueVerdier = avslag.formuegrunnlag?.tilFormueForBrev(),
        satsoversikt = satsoversikt?.toBrev(),
        sakstype = sakstype,
    )
}

fun Formuegrunnlag.tilFormueForBrev(): FormueForBrev {
    return FormueForBrev(
        søkersFormue = this.søkersFormue.tilFormueVerdierForBrev(),
        epsFormue = this.epsFormue?.tilFormueVerdierForBrev(),
        totalt = this.sumFormue(),
    )
}

fun Formueverdier.tilFormueVerdierForBrev(): FormueVerdierForBrev {
    return FormueVerdierForBrev(
        verdiSekundærBoliger = this.verdiEiendommer + this.verdiIkkePrimærbolig,
        verdiSekundærKjøretøyer = this.verdiKjøretøy,
        pengerIBanken = this.innskudd,
        depositumskonto = this.depositumskonto,
        pengerIKontanter = this.kontanter,
        aksjerOgVerdiPapir = this.verdipapir,
        pengerSøkerSkyldes = this.pengerSkyldt,
    )
}

fun Avslagsgrunn.toBrev(): AvslagsgrunnBrev = when (this) {
    Avslagsgrunn.UFØRHET -> AvslagsgrunnBrev.UFØRHET
    Avslagsgrunn.FLYKTNING -> AvslagsgrunnBrev.FLYKTNING
    Avslagsgrunn.OPPHOLDSTILLATELSE -> AvslagsgrunnBrev.OPPHOLDSTILLATELSE
    Avslagsgrunn.PERSONLIG_OPPMØTE -> AvslagsgrunnBrev.PERSONLIG_OPPMØTE
    Avslagsgrunn.FORMUE -> AvslagsgrunnBrev.FORMUE
    Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE -> AvslagsgrunnBrev.BOR_OG_OPPHOLDER_SEG_I_NORGE
    Avslagsgrunn.FOR_HØY_INNTEKT -> AvslagsgrunnBrev.FOR_HØY_INNTEKT
    Avslagsgrunn.SU_UNDER_MINSTEGRENSE -> AvslagsgrunnBrev.SU_UNDER_MINSTEGRENSE
    Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER -> AvslagsgrunnBrev.UTENLANDSOPPHOLD_OVER_90_DAGER
    Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON -> AvslagsgrunnBrev.INNLAGT_PÅ_INSTITUSJON
    Avslagsgrunn.MANGLENDE_DOKUMENTASJON -> AvslagsgrunnBrev.MANGLENDE_DOKUMENTASJON
    Avslagsgrunn.SØKNAD_MANGLER_DOKUMENTASJON -> AvslagsgrunnBrev.SØKNAD_MANGLER_DOKUMENTASJON
    Avslagsgrunn.ALDERSPENSJON_FOLKETRYGDEN -> AvslagsgrunnBrev.ALDERSPENSJON_FOLKETRYGDEN
    Avslagsgrunn.ALDERSPENSJON_ANDRE_NORSKE_PENSJONSORDNINGER -> AvslagsgrunnBrev.ALDERSPENSJON_ANDRE_NORSKE_PENSJONSORDNINGER
    Avslagsgrunn.ALDERSPENSJON_UTENLANDSKE_PENSJONSORDNINGER -> AvslagsgrunnBrev.ALDERSPENSJON_UTENLANDSKE_PENSJONSORDNINGER
    Avslagsgrunn.FAMILIEGJENFORENING -> AvslagsgrunnBrev.FAMILIEGJENFORENING
}
