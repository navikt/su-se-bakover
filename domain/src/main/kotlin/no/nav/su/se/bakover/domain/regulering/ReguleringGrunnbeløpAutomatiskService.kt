package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Måned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

interface ReguleringGrunnbeløpAutomatiskService {
    fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
        grunnbeløpRegulering: Boolean = true,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )
}

sealed interface BleIkkeRegulert {
    val saksnummer: Saksnummer

    data class FantIkkeSak(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    sealed interface TrengerIkkeRegulere : BleIkkeRegulert {
        data class IkkeLøpendeSak(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeRegulere

        data class AlleredeRegulert(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeRegulere

        data class FinnesÅpenRegulering(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeRegulere
    }

    sealed interface ReguleringFeiletVedKlargjøring : BleIkkeRegulert {
        data class FeilunderVurderingAvVedtakstilstand(
            val feil: Throwable,
            override val saksnummer: Saksnummer,
        ) : ReguleringFeiletVedKlargjøring

        data class UthentingFradragEksterntFeilet(
            val feil: HentingAvEksterneReguleringerFeiletForBruker,
            override val saksnummer: Saksnummer,
        ) : ReguleringFeiletVedKlargjøring
    }

    /*
     * Kan bety regulering ikke er gjennomførbart med reguleringsbehandling pga vedtaksperioder som ikke støttes,
     * eller at behandling medfører flere endringer enn nytt grunnbeløp og må ha vanlig behandling med vedtaksbrev.
     */
    data class MåRegulereMedRevurdering(
        override val saksnummer: Saksnummer,
        val årsak: ÅrsakRevurdering,
    ) : BleIkkeRegulert

    data class KunneIkkeBehandleAutomatisk(
        val feil: KunneIkkeBehandleRegulering?,
        val feilmelding: String? = null,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert
}

fun BleIkkeRegulert.toResultat(
    utfall: Reguleringsresultat.Utfall,
    beskrivelse: String = "",
) = Reguleringsresultat(
    saksnummer = saksnummer,
    utfall = utfall,
    beskrivelse = beskrivelse,
)

data class ÅrsakRevurdering(
    val årsak: Årsak,
    val diffBeløp: List<BeløpMedDiff> = emptyList(),
) {

    enum class Årsak {
        DIFFERANSE_MED_EKSTERNE_BELØP,
        REGULERING_BLIR_FEILUTBETALING,
        REGULERING_ER_OVER_TOLERANSEGRENSE,
        REGULERING_FØRER_TIL_AVSLAG,
        AAP_MANGLER_GYLDIG_PERIODE,
    }

    sealed class BeløpMedDiff {
        abstract val eksisterendeBeløp: BigDecimal
        abstract val nyttBeløp: BigDecimal

        data class Fradrag(
            override val eksisterendeBeløp: BigDecimal,
            override val nyttBeløp: BigDecimal,
            val fradragstype: Fradragstype,
            val tilhører: FradragTilhører,
        ) : BeløpMedDiff()

        data class BeregningOverToleranse(
            override val eksisterendeBeløp: BigDecimal,
            override val nyttBeløp: BigDecimal,
            val toleransegrense: BigDecimal,
        ) : BeløpMedDiff()
    }
}

fun Regulering.toReguleringForLogResultat(): ReguleringOppsummering {
    return ReguleringOppsummering(
        saksnummer = saksnummer,
        behandlingsId = id.value,
        periode = periode,
        reguleringstype = reguleringstype,
        erIverksatt = this is IverksattRegulering,
        regulertBeregning = beregning?.getMånedsberegninger()?.map {
            ReguleringBeregningOppsummering(
                periode = it.periode,
                sumYtelse = it.getSumYtelse(),
                benyttetG = it.getBenyttetGrunnbeløp(),
                sats = it.getSatsbeløp(),
            )
        },
    )
}

fun ReguleringOppsummering.toResultat(
    beskrivelse: String,
    utfall: Reguleringsresultat.Utfall,
) = Reguleringsresultat(
    saksnummer = saksnummer,
    behandlingsId = behandlingsId,
    utfall = utfall,
    beskrivelse = beskrivelse,
)
