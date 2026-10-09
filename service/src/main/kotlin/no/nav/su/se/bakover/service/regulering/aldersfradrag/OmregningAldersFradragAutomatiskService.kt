package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBehandleRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.util.UUID

interface OmregningAldersFradragAutomatiskService {
    fun startAutomatiskOmregning(fraOgMedMåned: Måned): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>>
    fun startAutomatiskOmregningForInnsyn(fraOgMedMåned: Måned, maksAntallSaker: Int?, saksnummer: String?): List<Either<BleIkkeOmregnetAlder, ReguleringOppsummering>>
}

sealed interface BleIkkeOmregnetAlder {
    val saksnummer: Saksnummer

    sealed interface TrengerIkkeOmregne : BleIkkeOmregnetAlder {
        data class IkkeLøpendeSak(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeOmregne

        data class FinnesÅpenOmregning(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeOmregne

        data class HarIkkeAlderspensjonFradrag(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeOmregne

        data class HarStans(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeOmregne

        data class ErUnder10ProsentEndring(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeOmregne
    }

    data class FlereÅpneReguleringer(
        override val saksnummer: Saksnummer,
        val antall: Long,
    ) : BleIkkeOmregnetAlder

    data class FeilunderVurderingAvVedtakstilstand(
        val feil: Throwable,
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder

    data class UthentingFradragEksterntFeilet(
        val feil: HentingAvEksterneReguleringerFeiletForBruker,
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder

    data class ManglerEpsForAlderspensjonsfradrag(
        val sakId: UUID,
        val måned: Måned,
        override val saksnummer: Saksnummer,
        val tilhører: FradragTilhører = FradragTilhører.EPS,
    ) : BleIkkeOmregnetAlder

    /**
     * Gjeldende vedtak starter etter omregningsmåneden, for eksempel en ny søknad som gjelder fra neste måned.
     * Saken må vurderes manuelt.
     */
    data class VedtakStarterEtterOmregningsmåned(
        val omregningsmåned: Måned,
        val vedtaksperiode: Periode,
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder

    /**
     * Et norsk alderspensjonsfradrag starter etter omregningsmåneden. Pesys slås bare opp for omregningsmåneden,
     * så vi har ikke et eksternt beløp for disse fradragene. Saken må vurderes manuelt.
     */
    data class AlderspensjonsfradragStarterEtterOmregningsmåned(
        val omregningsmåned: Måned,
        val fradrag: List<FradragEtterOmregningsmåned>,
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder {
        data class FradragEtterOmregningsmåned(
            val tilhører: FradragTilhører,
            val periode: Periode,
        )
    }

    data class FeilUnderOpprettelseAvBehandling(
        val feil: Throwable,
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder

    sealed interface KunneIkkeBehandleAutomatisk : BleIkkeOmregnetAlder {
        data class KjentFeil(
            val feil: KunneIkkeBehandleRegulering,
            override val saksnummer: Saksnummer,
        ) : KunneIkkeBehandleAutomatisk

        data class UkjentFeil(
            val feil: Throwable,
            override val saksnummer: Saksnummer,
        ) : KunneIkkeBehandleAutomatisk
    }
}

data class StartAutomatiskOmregningForInnsynCommand(
    val fraOgMedMåned: Måned,
    val maksAntallSaker: Int? = null,
    val saksnummer: String? = null,
)
