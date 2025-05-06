package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import person.domain.Person
import java.time.Year

internal class AldersvurderingTest {
    @Nested
    inner class Alder {
        @Test
        fun `person har ikke noe fødselsdata - Maskinell vurdering ukjent uten fødselsår`() {
            val person = person(fødsel = null)
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.ALDER,
            ).shouldBeLeft().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.UtenFødselsår(
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = null,
                    alderSøkerFyllerIÅr = null,
                    alderPåTidspunkt = null,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.saksbehandlersAvgjørelse shouldBe null
                it.fødselsdato shouldBe null
                it.fødselsår shouldBe null
            }
        }

        @Test
        fun `person har bare fødselsår, men er over 67(68) - Maskinell vurdering rett på alder`() {
            val fødselsår = Year.of(1953)
            val person = person(fødsel = Person.Fødsel.MedFødselsår(fødselsår))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.ALDER,
            ).shouldBeRight().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.RettPaaAlderSU.MedFødselsår(
                    fødselsår,
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = null,
                    alderSøkerFyllerIÅr = 68,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.fødselsdato shouldBe null
                it.fødselsår shouldBe fødselsår
            }
        }

        @Test
        fun `person har bare fødselsår er 67 - Maskinell vurdering gir rett på alder`() {
            val fødselsår = Year.of(1954)
            val person = person(fødsel = Person.Fødsel.MedFødselsår(fødselsår))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.ALDER,
            ).shouldBeRight().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.RettPaaAlderSU.MedFødselsår(
                    fødselsår,
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = null,
                    alderSøkerFyllerIÅr = 67,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.fødselsdato shouldBe null
                it.fødselsår shouldBe fødselsår
            }
        }

        @Test
        fun `person har fødselsdato og er 67 år - Maskinell vurdering gir rett på alder`() {
            val januar = 1.januar(1954)

            val person = person(fødsel = Person.Fødsel.MedFødselsdato(januar))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.ALDER,
            ).shouldBeRight().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.RettPaaAlderSU.MedFødselsdato(
                    januar,
                    Year.of(januar.year),
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = 67,
                    alderSøkerFyllerIÅr = 67,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.fødselsdato shouldBe januar
            }
        }

        @Test
        fun `person har fødselsdato og er 68 år - Maskinell vurdering gir rett på alder`() {
            val januar = 1.januar(1953)

            val person = person(fødsel = Person.Fødsel.MedFødselsdato(januar))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.ALDER,
            ).shouldBeRight().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.RettPaaAlderSU.MedFødselsdato(
                    januar,
                    Year.of(januar.year),
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = 68,
                    alderSøkerFyllerIÅr = 68,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.fødselsdato shouldBe januar
            }
        }

        @Test
        fun `person har fødselsdato for ung for alder(66) må være minst 67 fra 1 virk`() {
            val januar = 1.januar(1955)

            val person = person(fødsel = Person.Fødsel.MedFødselsdato(januar))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.ALDER,
            ).shouldBeLeft().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPaaAlder.MedFødselsdato(
                    januar,
                    Year.of(januar.year),
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = 66,
                    alderSøkerFyllerIÅr = 66,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.fødselsdato shouldBe januar
            }
        }

        @Test
        fun `person har fødselsdato for ung for alder(31) må være minst 67 fra 1 virk`() {
            val januar = 1.januar(1990)

            val person = person(fødsel = Person.Fødsel.MedFødselsdato(januar))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.ALDER,
            ).shouldBeLeft().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPaaAlder.MedFødselsdato(
                    januar,
                    Year.of(januar.year),
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = 31,
                    alderSøkerFyllerIÅr = 31,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.fødselsdato shouldBe januar
            }
        }
    }

    @Nested
    inner class Ufoere {
        @Test
        fun `person har ikke noe fødselsdata - Maskinell vurdering ukjent uten fødselsår`() {
            val person = person(fødsel = null)
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.UFØRE,
            ).shouldBeLeft().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.UtenFødselsår(
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = null,
                    alderSøkerFyllerIÅr = null,
                    alderPåTidspunkt = null,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.saksbehandlersAvgjørelse shouldBe null
                it.fødselsdato shouldBe null
                it.fødselsår shouldBe null
            }
        }

        @Test
        fun `person har bare fødselsår, men er over 67 - Maskinell vurdering ikke rett på uføre`() {
            val person = person(fødsel = Person.Fødsel.MedFødselsår(Year.of(1953)))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.UFØRE,
            ).shouldBeLeft().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsår(
                    Year.of(1953),
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = null,
                    alderSøkerFyllerIÅr = 68,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.saksbehandlersAvgjørelse shouldBe null
                it.fødselsdato shouldBe null
                it.fødselsår shouldBe Year.of(1953)
            }
        }

        @Test
        fun `person har bare fødselsår, men er under under 67 - Maskinell vurdering rettPåUføre`() {
            val person = person(fødsel = Person.Fødsel.MedFødselsår(Year.of(1960)))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.UFØRE,
            ).shouldBeRight().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsår(
                    Year.of(1960),
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = null,
                    alderSøkerFyllerIÅr = 61,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.saksbehandlersAvgjørelse shouldBe null
                it.fødselsdato shouldBe null
                it.fødselsår shouldBe Year.of(1960)
            }
        }

        @Test
        fun `person har bare fødselsår, men er i intervallet 66-67 år - Maskinell vurdering ukjent`() {
            val person = person(fødsel = Person.Fødsel.MedFødselsår(Year.of(1954)))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.UFØRE,
            ).shouldBeLeft().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.Ukjent.MedFødselsår(
                    Year.of(1954),
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = null,
                    alderSøkerFyllerIÅr = 67,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.saksbehandlersAvgjørelse shouldBe null
                it.fødselsdato shouldBe null
                it.fødselsår shouldBe Year.of(1954)
            }
        }

        @Test
        fun `person har fødselsdato, blir 67 på starten av sluttmåned av stønadsperioden - Maskinell vurdering RettTilUføre`() {
            val person = person(fødsel = Person.Fødsel.MedFødselsdato(1.august(1954)))
            val stønadsperiode = Stønadsperiode.create(januar(2021)..august(2021))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.UFØRE,
            ).shouldBeRight().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.RettPåUføre.MedFødselsdato(
                    1.august(1954),
                    Year.of(1954),
                    stønadsperiode,
                )
                it.stønadsperiode shouldBe stønadsperiode
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = 66,
                    alderSøkerFyllerIÅr = 67,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.saksbehandlersAvgjørelse shouldBe null
                it.fødselsdato shouldBe 1.august(1954)
                it.fødselsår shouldBe Year.of(1954)
            }
        }

        @Test
        fun `person har fødselsdato, blir 67 måneden før slutten av stønadsperioden - Maskinell vurdering ikke rett til uføre`() {
            val person = person(fødsel = Person.Fødsel.MedFødselsdato(31.juli(1954)))
            val stønadsperiode = Stønadsperiode.create(januar(2021)..august(2021))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.UFØRE,
            ).shouldBeLeft().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsdato(
                    31.juli(1954),
                    Year.of(1954),
                    stønadsperiode,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = 66,
                    alderSøkerFyllerIÅr = 67,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode
                it.saksbehandlersAvgjørelse shouldBe null
                it.fødselsdato shouldBe 31.juli(1954)
                it.fødselsår shouldBe Year.of(1954)
            }
        }

        @Test
        fun `person har fødselsdato, er over 67 - Maskinell vurdering ikke rett til uføre`() {
            val person = person(fødsel = Person.Fødsel.MedFødselsdato(31.juli(1950)))
            Aldersvurdering.Vurdert.vurder(
                stønadsperiode = stønadsperiode2021,
                person = person,
                saksbehandlersAvgjørelse = null,
                clock = fixedClock,
                saksType = Sakstype.UFØRE,
            ).shouldBeLeft().let {
                it.maskinellVurdering shouldBe MaskinellAldersvurderingMedGrunnlagsdata.IkkeRettPåUføre.MedFødselsdato(
                    31.juli(1950),
                    Year.of(1950),
                    stønadsperiode2021,
                )
                it.aldersinformasjon shouldBe Aldersinformasjon.createFromExisting(
                    alder = 70,
                    alderSøkerFyllerIÅr = 71,
                    alderPåTidspunkt = fixedTidspunkt,
                )
                it.stønadsperiode shouldBe stønadsperiode2021
                it.saksbehandlersAvgjørelse shouldBe null
                it.fødselsdato shouldBe 31.juli(1950)
                it.fødselsår shouldBe Year.of(1950)
            }
        }
    }
}
