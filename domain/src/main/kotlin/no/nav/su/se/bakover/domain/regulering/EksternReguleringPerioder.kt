package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.util.UUID

/**
 * Rådata fra eksternt system (Pesys/AAP) for en sak, lagret per reguleringskjøring for senere analyse.
 *
 * @property kjøringId Knytter raden til en bestemt [ReguleringKjøring].
 * @property saksnummer Saken radene gjelder for.
 * @property tilhører Om periodene gjelder bruker eller EPS.
 * @property eksternKilde Kilden periodene kommer fra (PESYS eller AAP).
 * @property perioder Listen med eksterne perioder hentet for denne saken og kilden.
 */
data class EksternReguleringPerioder(
    val kjøringId: UUID,
    val saksnummer: Saksnummer,
    val tilhører: FradragTilhører,
    val eksternKilde: EksternKilde,
    val perioder: List<EksternPeriode>,
)

enum class EksternKilde {
    PESYS,
    AAP,
}

interface EksternReguleringPerioderRepo {
    fun lagre(perioder: List<EksternReguleringPerioder>)
    fun hentForKjøring(kjøringId: UUID): List<EksternReguleringPerioder>
}
