package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import java.time.Year

internal class VurdertStønadsperiodeOppMotPersonsAlderTest {

    @Test
    fun `person har ikke noe fødselsdata - Saksbehandler må kontrollere alder manuelt`() {
        val vurdertUtenFødselsdata = VurdertStønadsperiodeOppMotPersonsAlder.vurder(
            stønadsperiode = stønadsperiode2021,
            person(),
        )
        vurdertUtenFødselsdata shouldBe VurdertStønadsperiodeOppMotPersonsAlder.RettPåUføre.SaksbehandlerMåKontrollereManuelt(
            stønadsperiode = stønadsperiode2021,
            vilkår = Aldersvilkår.Ukjent.UtenFødselsår,
        )
    }

    @Test
    fun `person har bare fødselsår, men er over 67 - melder om at søker er for gammel`() {
        val vurdertUtenFødselsdata = VurdertStønadsperiodeOppMotPersonsAlder.vurder(
            stønadsperiode = stønadsperiode2021,
            person(
                fødsel = Person.Fødsel(
                    år = Year.of(1953),
                ),
            ),
        )
        vurdertUtenFødselsdata shouldBe VurdertStønadsperiodeOppMotPersonsAlder.SøkerErForGammel(
            vilkår = Aldersvilkår.RettPåAlder.MedFødselsår(Year.of(1953)),
        )
    }

    @Test
    fun `person har bare fødselsår, men er under under 67 - kontrollert automatisk`() {
        val vurdertUtenFødselsdata = VurdertStønadsperiodeOppMotPersonsAlder.vurder(
            stønadsperiode = stønadsperiode2021,
            person(
                fødsel = Person.Fødsel(
                    år = Year.of(1955),
                ),
            ),
        )
        vurdertUtenFødselsdata shouldBe VurdertStønadsperiodeOppMotPersonsAlder.RettPåUføre.KontrollertAutomatisk(
            stønadsperiode = stønadsperiode2021,
            vilkår = Aldersvilkår.RettPåUføre.MedFødselsår(Year.of(1955)),
        )
    }

    @Test
    fun `person har bare fødselsår, men er i intervallet 66-67 år - saksbehandler må kontrollere manuelt`() {
        val vurdertUtenFødselsdata = VurdertStønadsperiodeOppMotPersonsAlder.vurder(
            stønadsperiode = stønadsperiode2021,
            person(
                fødsel = Person.Fødsel(
                    år = Year.of(1954),
                ),
            ),
        )
        vurdertUtenFødselsdata shouldBe VurdertStønadsperiodeOppMotPersonsAlder.RettPåUføre.SaksbehandlerMåKontrollereManuelt(
            stønadsperiode = stønadsperiode2021,
            vilkår = Aldersvilkår.Ukjent.MedFødselsår(Year.of(1954)),
        )
    }

    @Test
    fun `person har fødselsdato, blir 67 på starten av sluttmåned av stønadsperioden - Kontrollert automatisk`() {
        val vurdertUtenFødselsdata = VurdertStønadsperiodeOppMotPersonsAlder.vurder(
            stønadsperiode = stønadsperiode2021,
            person(
                fødsel = Person.Fødsel(
                    dato = 1.desember(1954),
                    år = Year.of(1954),
                ),
            ),
        )
        vurdertUtenFødselsdata shouldBe VurdertStønadsperiodeOppMotPersonsAlder.RettPåUføre.KontrollertAutomatisk(
            stønadsperiode = stønadsperiode2021,
            vilkår = Aldersvilkår.RettPåUføre.MedFødselsdato(1.desember(1954)),
        )
    }

    @Test
    fun `person har fødselsdato, blir 67 måneden før slutten av stønadsperioden - Søker er for gammel`() {
        val vurdertUtenFødselsdata = VurdertStønadsperiodeOppMotPersonsAlder.vurder(
            stønadsperiode = stønadsperiode2021,
            person(
                fødsel = Person.Fødsel(
                    dato = 30.november(1954),
                    år = Year.of(1954),
                ),
            ),
        )
        vurdertUtenFødselsdata shouldBe VurdertStønadsperiodeOppMotPersonsAlder.SøkerErForGammel(
            vilkår = Aldersvilkår.RettPåAlder.MedFødselsdato(30.november(1954)),
        )
    }
}
