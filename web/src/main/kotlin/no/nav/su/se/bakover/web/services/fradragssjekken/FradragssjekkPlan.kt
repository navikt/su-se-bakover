package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

/**
 * 1. Lag sjekkpunkter for bruker
 *    - UFØRE-sak -> bruker får UFØR + AAP
 *    - ALDER-sak -> bruker får AP
 *
 * 2. Finn EPS for aktuell måned
 *    - ingen EPS -> stopp der
 *    - EPS finnes -> finn kategori:
 *      - under 67
 *      - 67 eller eldre
 *
 * 3. Lag sjekkpunkter for EPS
 *    - UFØRE-sak + EPS under 67 -> UFØR + AAP
 *    - UFØRE-sak + EPS 67+ -> AP
 *    - ALDER-sak + EPS under 67 -> UFØR + AAP
 *    - ALDER-sak + EPS 67+ -> AP
 *
 */
internal fun lagSjekkplanForSak(
    sak: SakInfo,
    gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    måned: Måned,
): SjekkPlan? {
    val sjekkpunkter = buildList {
        addAll(
            gjeldendeVedtaksdata.sjekkpunkterForBruker(
                sakstype = sak.type,
                fnr = sak.fnr,
                måned = måned,
            ),
        )

        gjeldendeVedtaksdata.gjeldendeEpsForMåned(måned)?.let { eps ->
            addAll(
                gjeldendeVedtaksdata.sjekkpunkterForEps(
                    sakstype = sak.type,
                    epsFnr = eps.fnr,
                    epsKategori = eps.kategori,
                    måned = måned,
                ),
            )
        }
    }

    return sjekkpunkter.takeIf { it.isNotEmpty() }?.let {
        SjekkPlan(sak = sak, sjekkpunkter = it)
    }
}

private data class EpsForMåned(
    val fnr: Fnr,
    val kategori: EpsKategori,
)

private fun GjeldendeVedtaksdata.gjeldendeEpsForMåned(
    måned: Måned,
): EpsForMåned? {
    val bosituasjonForMåned = grunnlagsdata.bosituasjonSomFullstendig()
        .singleOrNull { it.periode.inneholder(måned) }

    return when (bosituasjonForMåned) {
        null,
        is Bosituasjon.Fullstendig.Enslig,
        is Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen,
        -> null

        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> {
            EpsForMåned(
                fnr = bosituasjonForMåned.fnr,
                kategori = EpsKategori.SEKSTISYV_ELLER_ELDRE,
            )
        }

        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning,
        is Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning,
        -> {
            EpsForMåned(
                fnr = bosituasjonForMåned.fnr,
                kategori = EpsKategori.UNDER_SEKSTISYV,
            )
        }
    }
}

private fun GjeldendeVedtaksdata.sjekkpunkterForBruker(
    sakstype: Sakstype,
    fnr: Fnr,
    måned: Måned,
): List<Sjekkpunkt> {
    return when (sakstype) {
        Sakstype.UFØRE -> listOf(
            sjekkpunkt(
                fnr = fnr,
                tilhører = FradragTilhører.BRUKER,
                fradragstype = Fradragstype.Uføretrygd,
                ytelse = EksternYtelse.PESYS_UFORE,
                måned = måned,
            ),
            sjekkpunkt(
                fnr = fnr,
                tilhører = FradragTilhører.BRUKER,
                fradragstype = Fradragstype.Arbeidsavklaringspenger,
                ytelse = EksternYtelse.AAP,
                måned = måned,
            ),
        )

        Sakstype.ALDER -> listOf(
            sjekkpunkt(
                fnr = fnr,
                tilhører = FradragTilhører.BRUKER,
                fradragstype = Fradragstype.Alderspensjon,
                ytelse = EksternYtelse.PESYS_ALDER,
                måned = måned,
            ),
        )
    }
}

private fun GjeldendeVedtaksdata.sjekkpunkterForEps(
    sakstype: Sakstype,
    epsFnr: Fnr,
    epsKategori: EpsKategori,
    måned: Måned,
): List<Sjekkpunkt> {
    return when (sakstype) {
        Sakstype.UFØRE -> when (epsKategori) {
            EpsKategori.UNDER_SEKSTISYV -> listOf(
                sjekkpunkt(
                    fnr = epsFnr,
                    tilhører = FradragTilhører.EPS,
                    fradragstype = Fradragstype.Uføretrygd,
                    ytelse = EksternYtelse.PESYS_UFORE,
                    måned = måned,
                ),
                sjekkpunkt(
                    fnr = epsFnr,
                    tilhører = FradragTilhører.EPS,
                    fradragstype = Fradragstype.Arbeidsavklaringspenger,
                    ytelse = EksternYtelse.AAP,
                    måned = måned,
                ),
            )

            EpsKategori.SEKSTISYV_ELLER_ELDRE -> listOf(
                sjekkpunkt(
                    fnr = epsFnr,
                    tilhører = FradragTilhører.EPS,
                    fradragstype = Fradragstype.Alderspensjon,
                    ytelse = EksternYtelse.PESYS_ALDER,
                    måned = måned,
                ),
            )
        }

        Sakstype.ALDER -> when (epsKategori) {
            EpsKategori.UNDER_SEKSTISYV -> listOf(
                sjekkpunkt(
                    fnr = epsFnr,
                    tilhører = FradragTilhører.EPS,
                    fradragstype = Fradragstype.Uføretrygd,
                    ytelse = EksternYtelse.PESYS_UFORE,
                    måned = måned,
                ),
                sjekkpunkt(
                    fnr = epsFnr,
                    tilhører = FradragTilhører.EPS,
                    fradragstype = Fradragstype.Arbeidsavklaringspenger,
                    ytelse = EksternYtelse.AAP,
                    måned = måned,
                ),
            )

            EpsKategori.SEKSTISYV_ELLER_ELDRE -> listOf(
                sjekkpunkt(
                    fnr = epsFnr,
                    tilhører = FradragTilhører.EPS,
                    fradragstype = Fradragstype.Alderspensjon,
                    ytelse = EksternYtelse.PESYS_ALDER,
                    måned = måned,
                ),
            )
        }
    }
}

private fun GjeldendeVedtaksdata.sjekkpunkt(
    fnr: Fnr,
    tilhører: FradragTilhører,
    fradragstype: Fradragstype,
    ytelse: EksternYtelse,
    måned: Måned,
): Sjekkpunkt {
    return Sjekkpunkt(
        fnr = fnr,
        tilhører = tilhører,
        fradragstype = fradragstype,
        ytelse = ytelse,
        lokaltBeløp = lokaltFradragsbeløp(fradragstype, tilhører, måned),
    )
}

private fun GjeldendeVedtaksdata.lokaltFradragsbeløp(
    fradragstype: Fradragstype,
    tilhører: FradragTilhører,
    måned: Måned,
): Double? {
    val relevanteFradrag = grunnlagsdata.fradragsgrunnlag.filter {
        it.fradragstype == fradragstype &&
            it.tilhører == tilhører &&
            it.periode.inneholder(måned)
    }

    return relevanteFradrag.takeIf { it.isNotEmpty() }?.sumOf { it.månedsbeløp }
}
