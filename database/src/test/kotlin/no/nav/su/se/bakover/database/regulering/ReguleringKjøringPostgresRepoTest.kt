import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class ReguleringKjøringPostgresRepoTest(private val dataSource: DataSource) {
    @Test
    fun `lagrer reguleringskjøring i databasen`() {
        val testDataHelper = TestDataHelper(dataSource)
        val reguleringKjøringRepo = testDataHelper.reguleringKjøringRepo
        val reguleringKjøring = lagTestReguleringKjøring()
        reguleringKjøringRepo.lagre(reguleringKjøring)
        val result = reguleringKjøringRepo.hent()
        result.size shouldBe 1
        result.single() shouldBe reguleringKjøring
    }

    private fun lagTestReguleringKjøring() = ReguleringKjøring(
        id = UUID.randomUUID(),
        aar = 2021,
        type = ReguleringKjøring.REGULERINGSTYPE_GRUNNBELØP,
        dryrun = true,
        startTid = LocalDateTime.of(2026, 1, 1, 12, 0),
        sakerAntall = 7,
        sakerIkkeLøpende = listOf(
            Reguleringsresultat(
                saksnummer = Saksnummer(2021),
                behandlingsId = UUID.randomUUID(),
                utfall = Reguleringsresultat.Utfall.IKKE_LOEPENDE,
                beskrivelse = "Ingen løpende vedtak",
            ),
        ),
        sakerAlleredeRegulert = listOf(
            Reguleringsresultat(
                saksnummer = Saksnummer(2022),
                behandlingsId = UUID.randomUUID(),
                utfall = Reguleringsresultat.Utfall.ALLEREDE_REGULERT,
                beskrivelse = "Allerede regulert",
            ),
        ),
        sakerMåRevurderes = listOf(
            Reguleringsresultat(
                saksnummer = Saksnummer(2023),
                behandlingsId = UUID.randomUUID(),
                utfall = Reguleringsresultat.Utfall.MÅ_REVURDERE,
                beskrivelse = "Må revurderes manuelt",
            ),
        ),
        reguleringerSomFeilet = listOf(
            Reguleringsresultat(
                saksnummer = Saksnummer(2024),
                behandlingsId = UUID.randomUUID(),
                utfall = Reguleringsresultat.Utfall.FEILET,
                beskrivelse = "Regulering feilet",
            ),
        ),
        reguleringerAlleredeÅpen = listOf(
            Reguleringsresultat(
                saksnummer = Saksnummer(2025),
                behandlingsId = UUID.randomUUID(),
                utfall = Reguleringsresultat.Utfall.AAPEN_REGULERING,
                beskrivelse = "Har åpen regulering",
            ),
        ),
        reguleringerManuell = listOf(
            Reguleringsresultat(
                saksnummer = Saksnummer(2026),
                behandlingsId = UUID.randomUUID(),
                utfall = Reguleringsresultat.Utfall.MANUELL,
                beskrivelse = "DifferanseEtterRegulering",
            ),
        ),
        reguleringerAutomatisk = listOf(
            Reguleringsresultat(
                saksnummer = Saksnummer(2027),
                behandlingsId = UUID.randomUUID(),
                utfall = Reguleringsresultat.Utfall.AUTOMATISK,
                beskrivelse = "Fullført automatisk",
            ),
        ),
    )
}
