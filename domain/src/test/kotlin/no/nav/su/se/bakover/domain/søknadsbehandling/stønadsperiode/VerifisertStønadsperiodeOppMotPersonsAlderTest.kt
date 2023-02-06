package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.stønadsperiode2021
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Year

internal class VerifisertStønadsperiodeOppMotPersonsAlderTest {

    @Nested
    inner class PersonErNull {
        @Test
        fun `endrer ikke stønadsperioden hvis vi ikke henter person - Varsler om at vi ikke fikk verifisert mot person`() {
            VerifisertStønadsperiodeOppMotPersonsAlder.verifiser(
                stønadsperiode2021,
                null,
                fixedClock,
            ) shouldBe VerifisertStønadsperiodeOppMotPersonsAlder.create(
                stønadsperiode2021,
                VerifiseringsMelding.KunneIkkeVerifisereMotPerson,
            ).right()
        }
    }

    @Nested
    inner class HarPerson {
        @Test
        fun `endrer ikke stønadsperioden hvis vi henter person, men fødselsinformasjon er null - Varsler om at vi ikke fikk verifisert mot person info`() {
            VerifisertStønadsperiodeOppMotPersonsAlder.verifiser(
                stønadsperiode2021,
                person(),
                fixedClock,
            ) shouldBe VerifisertStønadsperiodeOppMotPersonsAlder.create(
                stønadsperiode2021,
                VerifiseringsMelding.KunneIkkeVerifisereStønadsperiodeMotFødselsinformasjon,
            ).right()
        }

        @Nested
        inner class PersonUnder67 {
            @Test
            fun `Endrer ikke stønadsperiode hvis person er under 67 for hele stønadsperioden - Varsler om at alt er OK`() {
                VerifisertStønadsperiodeOppMotPersonsAlder.verifiser(
                    stønadsperiode2021,
                    person(
                        fødsel = Person.Fødsel(
                            dato = 25.januar(2000),
                            år = Year.of(2000),
                        ),
                    ),
                    fixedClock,
                ) shouldBe VerifisertStønadsperiodeOppMotPersonsAlder.create(
                    stønadsperiode2021,
                    VerifiseringsMelding.VerifisertOkPersonFyllerIkke67Plus,
                ).right()
            }
        }

        @Nested
        inner class PersonBlir67EllerEldre {
            @Test
            fun `Gir feil dersom person er allerede 67 eller eldre`() {
                VerifisertStønadsperiodeOppMotPersonsAlder.verifiser(
                    stønadsperiode2021,
                    person(
                        fødsel = Person.Fødsel(
                            dato = null,
                            år = Year.of(1953),
                        ),
                    ),
                    fixedClock,
                ) shouldBe Valideringsfeil.PersonEr67EllerEldre.left()
            }

            @Test
            fun `endrer ikke stønadsperioden hvis vi henter person men fødselsdato er null - Varsler om at vi ikke fikk verifisert mot detaljert person info`() {
                VerifisertStønadsperiodeOppMotPersonsAlder.verifiser(
                    stønadsperiode2021,
                    person(
                        fødsel = Person.Fødsel(
                            dato = null,
                            år = Year.of(1954),
                        ),
                    ),
                    fixedClock,
                ) shouldBe VerifisertStønadsperiodeOppMotPersonsAlder.create(
                    stønadsperiode2021,
                    VerifiseringsMelding.KunneIkkeVerifisereMotDetaljertFødselsinformasjon,
                ).right()
            }

            @Test
            fun `endrer stønadsperioden hvis vi henter person og har fødselsdato - Varsler om at vi har endret stønadsperioden`() {
                VerifisertStønadsperiodeOppMotPersonsAlder.verifiser(
                    stønadsperiode2021,
                    person(
                        fødsel = Person.Fødsel(
                            dato = 1.august(1954),
                            år = Year.of(1954),
                        ),
                    ),
                    fixedClock,
                ) shouldBe VerifisertStønadsperiodeOppMotPersonsAlder.create(
                    Stønadsperiode.create(Periode.create(1.januar(2021), 31.august(2021))),
                    VerifiseringsMelding.HarBegrensetStønadsperiode,
                ).right()
            }
        }
    }
}
