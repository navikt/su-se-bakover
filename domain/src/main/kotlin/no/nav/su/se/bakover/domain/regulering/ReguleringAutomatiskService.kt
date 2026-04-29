package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.periode.Måned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal

sealed interface BleIkkeRegulert {
    val saksnummer: Saksnummer

    data class FantIkkeSak(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class IkkeLøpendeSak(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    // TODO Ta i bruk denne..
    data class AlleredeRegulert(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class FinnesÅpenRegulering(
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    /*
     * Kan bety regulering ikke er gjennomførbart med reguleringsbehandling pga vedtaksperioder som ikke støttes,
     * eller at behandling medfører flere endringer enn nytt grunnbeløp og må ha vanlig behandling med vedtaksbrev.
     */
    data class MåRegulereMedRevurdering(
        override val saksnummer: Saksnummer,
        val årsak: ÅrsakRevurdering,
    ) : BleIkkeRegulert

    data class UthentingFradragPesysFeilet(
        val feil: HentingAvEksterneReguleringerFeiletForBruker,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert

    data class KunneIkkeBehandleAutomatisk(
        val feil: KunneIkkeBehandleRegulering,
        override val saksnummer: Saksnummer,
        val tidsbrukSekunder: Int,
    ) : BleIkkeRegulert

    data class UkjentFeil(
        val feil: Throwable,
        override val saksnummer: Saksnummer,
    ) : BleIkkeRegulert
}

fun BleIkkeRegulert.toResultat(
    utfall: Reguleringsresultat.Utfall,
    beskrivelse: String = "",
    tidsbrukSekunder: Int? = null,
) = Reguleringsresultat(
    saksnummer = saksnummer,
    utfall = utfall,
    beskrivelse = beskrivelse,
    tidsbrukSekunder = tidsbrukSekunder,
)

// Brukes når det må gjøres endringer som går utover en beregning med ny G
// eller når det av en eller annen grunn må sendes ut vedtaksbrev
data class ÅrsakRevurdering(
    val årsak: Årsak,
    val diffBeløp: List<BeløperMedDiff> = emptyList(),
) {

    enum class Årsak {
        IKKE_KONTINUERLIG_VEDTAKSLINJE,
        INKONSISTENTE_GRUNNLAG_OG_VILKÅR,
        DIFFERANSE_MED_EKSTERNE_BELØP,
        REGULERING_BLIR_FEILUTBETALING,
        REGULERING_ER_OVER_TOLERANSEGRENSE,
        REGULERING_FØRER_TIL_AVSLAG,
    }

    sealed class BeløperMedDiff {
        abstract val eksisterendeBeløp: BigDecimal
        abstract val nyttBeløp: BigDecimal

        data class Fradrag(
            override val eksisterendeBeløp: BigDecimal,
            override val nyttBeløp: BigDecimal,
            val fradragstype: Fradragstype,
            val tilhører: FradragTilhører,
        ) : BeløperMedDiff()

        data class BeregningOverToleranse(
            override val eksisterendeBeløp: BigDecimal,
            override val nyttBeløp: BigDecimal,
            val toleransegrense: BigDecimal,
        ) : BeløperMedDiff()
    }
}

interface ReguleringAutomatiskService {
    fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>>

    fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    )
}
