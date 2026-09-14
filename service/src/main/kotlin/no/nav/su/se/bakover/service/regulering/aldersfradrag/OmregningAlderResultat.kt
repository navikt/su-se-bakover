import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.service.regulering.grunnbeløp.tilReguleringsresultat

sealed interface BleIkkeOmregnetAlder {
    val saksnummer: Saksnummer

    data class FraReguleringsflyt(
        val resultat: BleIkkeRegulert,
    ) : BleIkkeOmregnetAlder {
        override val saksnummer: Saksnummer = resultat.saksnummer
    }
    data class ManglerAlderspensjonsfradrag(
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
                is BleIkkeOmregnetAlder.ManglerAlderspensjonsfradrag -> Reguleringsresultat(
                    saksnummer = bleIkkeOmregnet.saksnummer,
                    behandlingsId = null,
                    utfall = Reguleringsresultat.Utfall.MANGLER_ALDERSPENSJONSFRADRAG,
                    beskrivelse = bleIkkeOmregnet.toString(),
                )
                is BleIkkeOmregnetAlder.FraReguleringsflyt ->
                    bleIkkeOmregnet.resultat
                        .left()
                        .tilReguleringsresultat()
            }
        },
        ifRight = { omregningAlderOppsummering ->
            omregningAlderOppsummering.reguleringOppsummering
                .right()
                .tilReguleringsresultat()
        },
    )
