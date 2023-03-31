package no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class LeggTilBosituasjonRequest(
    val periode: Periode,
    val epsFnr: String?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
) {
    fun toDomain(
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, Grunnlag.Bosituasjon.Fullstendig> {
        val log = LoggerFactory.getLogger(this::class.java)

        if ((epsFnr == null && delerBolig == null) || (epsFnr != null && delerBolig != null)) {
            return KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData.left()
        }

        if (epsFnr != null) {
            val eps = hentPerson(Fnr(epsFnr)).getOrElse {
                return KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS.left()
            }

            val epsAlder = if (eps.getAlder(LocalDate.now(clock)) == null) {
                log.error("Alder på EPS er null. Denne har i tidligere PDL kall hatt en verdi")
                return KunneIkkeLeggeTilBosituasjongrunnlag.EpsAlderErNull.left()
            } else {
                eps.getAlder(LocalDate.now(clock))!!
            }

            return when {
                epsAlder >= 67 -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                    fnr = eps.ident.fnr,
                ).right()

                else -> when (ektemakeEllerSamboerUførFlyktning) {
                    true -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = periode,
                        fnr = eps.ident.fnr,
                    ).right()

                    false -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = periode,
                        fnr = eps.ident.fnr,
                    ).right()

                    null -> return KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData.left()
                }
            }
        }

        if (delerBolig != null) {
            return when (delerBolig) {
                true -> Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                ).right()

                false -> Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                ).right()
            }
        }

        return KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData.left()
    }
}
