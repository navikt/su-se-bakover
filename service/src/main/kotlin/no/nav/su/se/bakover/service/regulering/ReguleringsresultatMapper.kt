import arrow.core.Either
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.toResultat

/**
 * Oversetter et reguleringsresultat per sak (f.eks. et [BleIkkeRegulert]-utfall eller en
 * [ReguleringOppsummering]) til en felles [Reguleringsresultat] med utfall og beskrivelse.
 *
 * Resultatet brukes til å gruppere og telle utfallet av en kjøring, og til fremgangssnapshots
 * per batch.
 */
fun Either<BleIkkeRegulert, ReguleringOppsummering>.tilReguleringsresultat(): Reguleringsresultat = fold(
    ifLeft = { bleIkkeRegulert ->
        when (bleIkkeRegulert) {
            is BleIkkeRegulert.TrengerIkkeRegulere.IkkeLøpendeSak -> bleIkkeRegulert.toResultat(Reguleringsresultat.Utfall.IKKE_LOEPENDE)
            is BleIkkeRegulert.TrengerIkkeRegulere.AlleredeRegulert -> bleIkkeRegulert.toResultat(Reguleringsresultat.Utfall.ALLEREDE_REGULERT)
            is BleIkkeRegulert.TrengerIkkeRegulere.FinnesÅpenRegulering -> bleIkkeRegulert.toResultat(
                Reguleringsresultat.Utfall.AAPEN_REGULERING,
                bleIkkeRegulert.toString(),
            )

            is BleIkkeRegulert.MåRegulereMedRevurdering -> bleIkkeRegulert.toResultat(
                Reguleringsresultat.Utfall.MÅ_REVURDERE,
                bleIkkeRegulert.årsak.toString(),
            )

            is BleIkkeRegulert.FantIkkeSak,
            is BleIkkeRegulert.KunneIkkeBehandleAutomatisk,
            is BleIkkeRegulert.ReguleringFeiletVedKlargjøring.FeilunderVurderingAvVedtakstilstand,
            is BleIkkeRegulert.ReguleringFeiletVedKlargjøring.UthentingFradragEksterntFeilet,
            -> bleIkkeRegulert.toResultat(Reguleringsresultat.Utfall.FEILET, bleIkkeRegulert.toString())
        }
    },
    ifRight = { oppsummering ->
        when (val type = oppsummering.reguleringstype) {
            is Reguleringstype.MANUELL -> oppsummering.toResultat(
                utfall = Reguleringsresultat.Utfall.MANUELL,
                beskrivelse = type.problemer.joinToString(", ") { it.kategori.name },
            )

            Reguleringstype.AUTOMATISK -> oppsummering.toResultat(
                utfall = Reguleringsresultat.Utfall.AUTOMATISK,
                beskrivelse = oppsummering.toString(),
            )
        }
    },
)
