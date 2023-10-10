package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.domain.vilkår.UFØRETRYGD_MAX_ALDER
import person.domain.Person
import java.time.LocalDate
import java.time.Year

/**
 * Brukes per nå bare for SU-uføre. Kan utvides videre når SU-Alder skal inn
 *
 * Et vilkår for å få SU-uføre stønad en gitt måned er at du ikke har fylt 67 år forrige måned, eller tidligere.
 *
 * Grunnen til usikkerheten rundt fødselsdato er at folkeregisteret ikke kan garantere denne verdien.
 */
sealed interface MaskinellAldersvurderingMedGrunnlagsdata {
    val stønadsperiode: Stønadsperiode
    val fødselsdato: LocalDate?
    val fødselsår: Year?

    fun tidligsteGyldigeFødselsdato(): LocalDate = stønadsperiode.tidligsteGyldigeFødselsdato()

    companion object {
        // Hvis du har fødselsdag 1 dag før dette, så er du for gammel
        fun Stønadsperiode.tidligsteGyldigeFødselsdato(): LocalDate =
            periode.tilOgMed.startOfMonth().minusYears(UFØRETRYGD_MAX_ALDER.toLong())

        fun avgjørBasertPåFødselsdatoEllerFødselsår(
            stønadsperiode: Stønadsperiode,
            fødsel: Person.Fødsel?,
        ): MaskinellAldersvurderingMedGrunnlagsdata {
            val fødselsdato = getFødselsdato(fødsel)
            val fødselsår = getFødselsår(fødsel)
            val sisteMånedIPeriode = stønadsperiode.måneder().last()

            return if (fødselsdato != null) {
                avgjørBasertPåFødselsdato(
                    fødselsdato = fødselsdato,
                    fødselsår = fødselsår!!,
                    stønadsperiode = stønadsperiode,
                )
            } else if (fødselsår == null) {
                Ukjent.UtenFødselsår(stønadsperiode)
            } else {
                val sisteÅrIPeriode = sisteMånedIPeriode.årOgMåned.year
                avgjørBasertPåFødseslår(fødselsår, stønadsperiode, sisteÅrIPeriode)
            }
        }

        private fun getFødselsdato(fødsel: Person.Fødsel?): LocalDate? = when (fødsel) {
            is Person.Fødsel.MedFødselsdato -> fødsel.dato
            is Person.Fødsel.MedFødselsår -> null
            null -> null
        }

        private fun getFødselsår(fødsel: Person.Fødsel?): Year? = when (fødsel) {
            is Person.Fødsel.MedFødselsdato -> fødsel.år
            is Person.Fødsel.MedFødselsår -> fødsel.år
            null -> null
        }

        private fun avgjørBasertPåFødselsdato(
            fødselsdato: LocalDate,
            fødselsår: Year,
            stønadsperiode: Stønadsperiode,
        ): MaskinellAldersvurderingMedGrunnlagsdata {
            return if (fødselsdato < stønadsperiode.tidligsteGyldigeFødselsdato()) {
                IkkeRettPåUføre.MedFødselsdato(
                    fødselsdato = fødselsdato,
                    fødselsår = fødselsår,
                    stønadsperiode = stønadsperiode,
                )
            } else {
                RettPåUføre.MedFødselsdato(
                    fødselsdato = fødselsdato,
                    fødselsår = fødselsår,
                    stønadsperiode = stønadsperiode,
                )
            }
        }

        private fun avgjørBasertPåFødseslår(
            fødselsår: Year,
            stønadsperiode: Stønadsperiode,
            sisteÅrIPeriode: Int,
        ): MaskinellAldersvurderingMedGrunnlagsdata {
            return if (fødselsår.value < (sisteÅrIPeriode - 67)) {
                IkkeRettPåUføre.MedFødselsår(
                    fødselsår = fødselsår,
                    stønadsperiode = stønadsperiode,
                )
            } else if (fødselsår.value > (sisteÅrIPeriode - 67)) {
                RettPåUføre.MedFødselsår(fødselsår, stønadsperiode)
            } else {
                Ukjent.MedFødselsår(fødselsår, stønadsperiode)
            }
        }
    }

    sealed interface RettPåUføre : MaskinellAldersvurderingMedGrunnlagsdata {
        data class MedFødselsdato(
            override val fødselsdato: LocalDate,
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : RettPåUføre

        data class MedFødselsår(
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : RettPåUføre {
            override val fødselsdato: LocalDate? = null
        }
    }

    sealed interface IkkeRettPåUføre : MaskinellAldersvurderingMedGrunnlagsdata {
        data class MedFødselsdato(
            override val fødselsdato: LocalDate,
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : IkkeRettPåUføre

        data class MedFødselsår(
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : IkkeRettPåUføre {
            override val fødselsdato: LocalDate? = null
        }
    }

    sealed interface Ukjent : MaskinellAldersvurderingMedGrunnlagsdata {
        data class MedFødselsår(
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : Ukjent {
            override val fødselsdato: LocalDate? = null
        }

        data class UtenFødselsår(override val stønadsperiode: Stønadsperiode) : Ukjent {
            override val fødselsdato: LocalDate? = null
            override val fødselsår: Year? = null
        }
    }
}
