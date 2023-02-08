package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.person.Person
import java.time.LocalDate
import java.time.Year

/**
 * Brukes både for SU-alder og SU-uføre.
 *
 * Et vilkår for å få SU-uføre stønad en gitt måned er at du ikke har fylt 67 år forrige måned, eller tidligere.
 * Tilsvarende for å få SU-alder stønad en gitt måned er at du fylte 67 år forrige måned, eller tidligere.
 *
 * Grunnen til usikkerheten rundt fødselsdato er at folkeregisteret ikke kan garantere denne verdien.
 */
sealed interface Aldersvilkår {

    companion object {
        fun avgjørBasertPåFødselsdatoEllerFødselsår(
            periode: Periode,
            fødsel: Person.Fødsel?,
        ): Aldersvilkår {
            val fødselsdato = fødsel?.dato
            val fødselsår = fødsel?.år
            if (fødselsdato != null) {
                require(fødselsdato.year == fødselsår!!.value) { "Året på fødselsdatoen og fødselsåret som er angitt er ikke lik. fødelsdato ${fødselsdato.year}, fødselsår ${fødselsår.value}" }
            }
            val sisteMånedIPeriode = periode.måneder().last()
            // Hvis du har fødselsdag 1 dag før dette, så er du for gammel
            val tidligsteGyldigeFødselsdato = sisteMånedIPeriode.fraOgMed.minusYears(67)
            return if (fødselsdato != null) {
                if (fødselsdato < tidligsteGyldigeFødselsdato) {
                    RettPåAlder.MedFødselsdato(fødselsdato)
                } else {
                    RettPåUføre.MedFødselsdato(fødselsdato)
                }
            } else if (fødselsår == null) {
                Ukjent.UtenFødselsår
            } else {
                val sisteÅrIPeriode = sisteMånedIPeriode.årOgMåned.year
                if (fødselsår.value < (sisteÅrIPeriode - 67)) {
                    RettPåAlder.MedFødselsår(fødselsår)
                } else if (fødselsår.value > (sisteÅrIPeriode - 67)) {
                    RettPåUføre.MedFødselsår(fødselsår)
                } else {
                    Ukjent.MedFødselsår(fødselsår)
                }
            }
        }
    }

    sealed interface RettPåUføre : Aldersvilkår {
        val fødselsår: Year

        data class MedFødselsdato(val fødselsdato: LocalDate) : RettPåUføre {
            override val fødselsår: Year = Year.of(fødselsdato.year)
        }

        data class MedFødselsår(override val fødselsår: Year) : RettPåUføre
    }

    sealed interface RettPåAlder : Aldersvilkår {
        val fødselsår: Year

        data class MedFødselsdato(val fødselsdato: LocalDate) : RettPåAlder {
            override val fødselsår: Year = Year.of(fødselsdato.year)
        }

        data class MedFødselsår(override val fødselsår: Year) : RettPåAlder
    }

    sealed interface Ukjent : Aldersvilkår {
        data class MedFødselsår(val fødselsår: Year) : Ukjent
        object UtenFødselsår : Ukjent
    }
}

fun Person.aldersvilkår(periode: Periode): Aldersvilkår {
    return Aldersvilkår.avgjørBasertPåFødselsdatoEllerFødselsår(periode, this.fødsel)
}
