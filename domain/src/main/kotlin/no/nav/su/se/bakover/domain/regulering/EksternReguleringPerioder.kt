package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.util.UUID

/**
 * Rådata fra eksternt system (Pesys/AAP) for en sak, lagret per reguleringskjøring for senere analyse.
 * Lagres for både vellykkede og feilede oppslag — feilede oppslag har tom [perioder] og innholdet
 * i [feilkoder] forteller hvorfor oppslaget feilet for denne kombinasjonen.
 *
 * @property kjøringId Knytter raden til en bestemt [ReguleringKjøring].
 * @property saksnummer Saken radene gjelder for.
 * @property tilhører Om periodene gjelder bruker eller EPS.
 * @property eksternKilde Kilden periodene kommer fra (PESYS eller AAP).
 * @property perioder Rådata-perioder fra eksternt system. Tom liste ved feil eller når
 *                    kilden ikke leverer periodisert rådata (f.eks. AAP).
 * @property feilkoder [FeilMedEksternRegulering.feilkode] når oppslaget feilet. Tom
 *                     liste ved suksess.
 */
data class EksternReguleringPerioder(
    val kjøringId: UUID,
    val saksnummer: Saksnummer,
    val tilhører: FradragTilhører,
    val eksternKilde: EksternKilde,
    val perioder: List<EksternPeriode>,
    val feilkoder: List<String> = emptyList(),
)

enum class EksternKilde {
    PESYS,
    AAP,
}

interface EksternReguleringPerioderRepo {
    fun lagre(perioder: List<EksternReguleringPerioder>)
    fun hentForKjøring(kjøringId: UUID): List<EksternReguleringPerioder>
}
