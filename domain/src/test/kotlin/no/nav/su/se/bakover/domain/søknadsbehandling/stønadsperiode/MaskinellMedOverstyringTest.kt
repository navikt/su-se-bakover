package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Test
import java.time.Year

internal class MaskinellMedOverstyringTest {

    @Test
    fun `person har ikke noe fødselsdata - Saksbehandler må kontrollere alder manuelt`() {

    }

    @Test
    fun `person har bare fødselsår, men er over 67 - melder om at søker er for gammel`() {
    }

    @Test
    fun `person har bare fødselsår, men er under under 67 - kontrollert automatisk`() {
    }

    @Test
    fun `person har bare fødselsår, men er i intervallet 66-67 år - saksbehandler må kontrollere manuelt`() {
    }

    @Test
    fun `person har fødselsdato, blir 67 på starten av sluttmåned av stønadsperioden - Kontrollert automatisk`() {
    }

    @Test
    fun `person har fødselsdato, blir 67 måneden før slutten av stønadsperioden - Søker er for gammel`() {
    }
}
