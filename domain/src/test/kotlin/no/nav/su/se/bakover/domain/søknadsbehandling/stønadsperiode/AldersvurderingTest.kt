package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import person.domain.Person
import java.time.Year

internal class AldersvurderingTest {

    @Test
    fun `person har ikke noe fødselsdata - Maskinell vurdering ukjent uten fødselsår`() {
        val person = person(fødsel = null)
        Aldersvurdering.Vurdert.vurder(
            stønadsperiode = stønadsperiode2021,
            person = person,
            saksbehandlersAvgjørelse = null,
            clock = fixedClock,
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
    fun `person har bare fødselsår, men er over 67 - Maskinell vurdering ikke rett på alder`() {
        val person = person(fødsel = Person.Fødsel.MedFødselsår(Year.of(1953)))
        Aldersvurdering.Vurdert.vurder(
            stønadsperiode = stønadsperiode2021,
            person = person,
            saksbehandlersAvgjørelse = null,
            clock = fixedClock,
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
