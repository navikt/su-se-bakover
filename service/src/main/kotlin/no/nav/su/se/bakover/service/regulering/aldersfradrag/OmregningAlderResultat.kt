package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.KunneIkkeBehandleRegulering
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.service.regulering.grunnbeløp.tilReguleringsresultat

sealed interface BleIkkeOmregnetAlder {
    val saksnummer: Saksnummer

    sealed interface TrengerIkkeOmregne : BleIkkeOmregnetAlder {
        data class IkkeLøpendeSak(
            override val saksnummer: Saksnummer,
        ) : TrengerIkkeOmregne
    }

    sealed interface OmregningFeiletVedKlargjøring : BleIkkeOmregnetAlder {
        data class UthentingFradragEksterntFeilet(
            val feil: HentingAvEksterneReguleringerFeiletForBruker,
            override val saksnummer: Saksnummer,
        ) : OmregningFeiletVedKlargjøring
    }
    sealed interface OmregningFeiletVedBehandling : BleIkkeOmregnetAlder {
        data class KunneIkkeBehandleAutomatisk(
            val feil: KunneIkkeBehandleRegulering,
            override val saksnummer: Saksnummer,
        ) : OmregningFeiletVedBehandling
    }

    data class HarIkkeAlderspensjonFradrag(
        override val saksnummer: Saksnummer,
    ) : BleIkkeOmregnetAlder
}

data class OmregningAlderOppsummering(
    val reguleringOppsummering: ReguleringOppsummering,
)
fun Either<BleIkkeOmregnetAlder, OmregningAlderOppsummering>.tilReguleringsresultat(): Reguleringsresultat =
    fold(
        ifLeft = { bleIkkeOmregnet ->
            when (bleIkkeOmregnet) {
                is BleIkkeOmregnetAlder.TrengerIkkeOmregne.IkkeLøpendeSak -> Reguleringsresultat(
                    saksnummer = bleIkkeOmregnet.saksnummer,
                    behandlingsId = null,
                    utfall = Reguleringsresultat.Utfall.IKKE_LOEPENDE,
                    beskrivelse = bleIkkeOmregnet.toString(),
                )
                is BleIkkeOmregnetAlder.HarIkkeAlderspensjonFradrag ->
                    Reguleringsresultat(
                        saksnummer = bleIkkeOmregnet.saksnummer,
                        behandlingsId = null,
                        utfall = Reguleringsresultat.Utfall.FEILET,
                        beskrivelse = bleIkkeOmregnet.toString(),
                    )
                is BleIkkeOmregnetAlder.OmregningFeiletVedKlargjøring.UthentingFradragEksterntFeilet ->
                    Reguleringsresultat(
                        saksnummer = bleIkkeOmregnet.saksnummer,
                        behandlingsId = null,
                        utfall = Reguleringsresultat.Utfall.FEILET,
                        beskrivelse = bleIkkeOmregnet.toString(),
                    )

                is BleIkkeOmregnetAlder.OmregningFeiletVedBehandling.KunneIkkeBehandleAutomatisk ->
                    Reguleringsresultat(
                        saksnummer = bleIkkeOmregnet.saksnummer,
                        behandlingsId = null,
                        utfall = Reguleringsresultat.Utfall.FEILET,
                        beskrivelse = bleIkkeOmregnet.toString(),
                    )
            }
        },
        ifRight = { omregningAlderOppsummering ->
            omregningAlderOppsummering.reguleringOppsummering
                .right()
                .tilReguleringsresultat()
        },
    )
