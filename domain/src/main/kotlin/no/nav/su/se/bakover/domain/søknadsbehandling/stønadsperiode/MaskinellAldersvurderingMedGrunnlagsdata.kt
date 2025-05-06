package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.isEqualOrBefore
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import person.domain.Person
import vilkår.uføre.domain.ALDER_MINSTE_ALDER
import vilkår.uføre.domain.UFØRETRYGD_MAX_ALDER
import java.time.LocalDate
import java.time.Year

/**
 * Brukes per nå bare for SU-uføre og SU-alder.
 *
 * Et vilkår for å få SU-uføre stønad en gitt måned er at du ikke har fylt 67 år forrige måned, eller tidligere.
 *
 * Grunnen til usikkerheten rundt fødselsdato er at folkeregisteret ikke kan garantere denne verdien.
 */
sealed interface MaskinellAldersvurderingMedGrunnlagsdata {
    val stønadsperiode: Stønadsperiode
    val fødselsdato: LocalDate?
    val fødselsår: Year?

    fun tidligsteGyldigeFødselsdato(): LocalDate = stønadsperiode.tidligsteGyldigeFødselsdatoUfoere()

    companion object {
        // Hvis du har fødselsdag 1 dag før dette, så er du for gammel
        fun Stønadsperiode.tidligsteGyldigeFødselsdatoUfoere(): LocalDate =
            periode.tilOgMed.startOfMonth().minusYears(UFØRETRYGD_MAX_ALDER.toLong())

        fun Stønadsperiode.tidligsteGyldigeFødselsdatoAlder(): LocalDate =
            periode.fraOgMed.startOfMonth().minusYears(ALDER_MINSTE_ALDER.toLong())

        // TODO: tester for alle disse?
        fun avgjørBasertPåFødselsdatoEllerFødselsår(
            stønadsperiode: Stønadsperiode,
            fødsel: Person.Fødsel?,
            saksType: Sakstype,
        ): MaskinellAldersvurderingMedGrunnlagsdata {
            val fødselsdato = getFødselsdato(fødsel)
            val fødselsår = getFødselsår(fødsel)

            return if (fødselsdato != null) {
                avgjørBasertPåFødselsdato(
                    fødselsdato = fødselsdato,
                    fødselsår = fødselsår!!,
                    stønadsperiode = stønadsperiode,
                    saksType = saksType,
                )
            } else if (fødselsår == null) {
                Ukjent.UtenFødselsår(stønadsperiode)
            } else {
                avgjørBasertPåFødseslår(fødselsår, stønadsperiode, saksType = saksType)
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
            saksType: Sakstype,
        ): MaskinellAldersvurderingMedGrunnlagsdata {
            return if (saksType == Sakstype.UFØRE) {
                if (fødselsdato < stønadsperiode.tidligsteGyldigeFødselsdatoUfoere()) {
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
            } else {
                if (fødselsdato.isEqualOrBefore(stønadsperiode.tidligsteGyldigeFødselsdatoAlder())) {
                    RettPaaAlderSU.MedFødselsdato(
                        fødselsdato = fødselsdato,
                        fødselsår = fødselsår,
                        stønadsperiode = stønadsperiode,
                    )
                } else {
                    IkkeRettPaaAlder.MedFødselsdato(
                        fødselsdato = fødselsdato,
                        fødselsår = fødselsår,
                        stønadsperiode = stønadsperiode,
                    )
                }
            }
        }

        private fun avgjørBasertPåFødseslår(
            fødselsår: Year,
            stønadsperiode: Stønadsperiode,
            saksType: Sakstype,
        ): MaskinellAldersvurderingMedGrunnlagsdata {
            when (saksType) {
                Sakstype.ALDER -> {
                    val foersteMånedIPeriode = stønadsperiode.måneder().first()
                    val foersteAarIPeriode = foersteMånedIPeriode.årOgMåned.year
                    return if (fødselsår.value >= (foersteAarIPeriode - ALDER_MINSTE_ALDER)) {
                        RettPaaAlderSU.MedFødselsår(fødselsår, stønadsperiode)
                    } else if (fødselsår.value < (foersteAarIPeriode - ALDER_MINSTE_ALDER)) {
                        IkkeRettPaaAlder.MedFødselsår(fødselsår, stønadsperiode)
                    } else {
                        Ukjent.MedFødselsår(fødselsår, stønadsperiode)
                    }
                }
                Sakstype.UFØRE -> {
                    val sisteMånedIPeriode = stønadsperiode.måneder().last()
                    val sisteÅrIPeriode = sisteMånedIPeriode.årOgMåned.year
                    return if (fødselsår.value < (sisteÅrIPeriode - UFØRETRYGD_MAX_ALDER)) {
                        IkkeRettPåUføre.MedFødselsår(
                            fødselsår = fødselsår,
                            stønadsperiode = stønadsperiode,
                        )
                    } else if (fødselsår.value > (sisteÅrIPeriode - UFØRETRYGD_MAX_ALDER)) {
                        RettPåUføre.MedFødselsår(fødselsår, stønadsperiode)
                    } else {
                        Ukjent.MedFødselsår(fødselsår, stønadsperiode)
                    }
                }
            }
        }
    }

    sealed interface RettPaaAlderSU : MaskinellAldersvurderingMedGrunnlagsdata {
        data class MedFødselsdato(
            override val fødselsdato: LocalDate,
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : RettPaaAlderSU

        data class MedFødselsår(
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : RettPaaAlderSU {
            override val fødselsdato: LocalDate? = null
        }
    }

    sealed interface IkkeRettPaaAlder : MaskinellAldersvurderingMedGrunnlagsdata {
        data class MedFødselsdato(
            override val fødselsdato: LocalDate,
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : IkkeRettPaaAlder

        data class MedFødselsår(
            override val fødselsår: Year,
            override val stønadsperiode: Stønadsperiode,
        ) : IkkeRettPaaAlder {
            override val fødselsdato: LocalDate? = null
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
