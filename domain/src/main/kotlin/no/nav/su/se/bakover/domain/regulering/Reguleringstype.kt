package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.harForventetInntektStørreEnn0
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata

enum class Reguleringstype {
    AUTOMATISK,
    MANUELL;
}

fun GjeldendeVedtaksdata.utledReguleringstype(): Reguleringstype {
    if (this.grunnlagsdata.fradragsgrunnlag.any { (it.fradrag.fradragstype == Fradragstype.NAVytelserTilLivsopphold) || (it.fradrag.fradragstype == Fradragstype.OffentligPensjon) }) {
        return Reguleringstype.MANUELL
    }

    if (this.harStans()) {
        return Reguleringstype.MANUELL
    }

    if (this.vilkårsvurderinger.uføre.grunnlag.harForventetInntektStørreEnn0()) {
        return Reguleringstype.MANUELL
    }

    if (this.delerAvPeriodenErOpphør()) {
        return Reguleringstype.MANUELL
    }

    if (!this.tidslinjeForVedtakErSammenhengende()) {
        return Reguleringstype.MANUELL
    }

    if (this.pågåendeAvkortingEllerBehovForFremtidigAvkorting) {
        return Reguleringstype.MANUELL
    }

    return Reguleringstype.AUTOMATISK
}
