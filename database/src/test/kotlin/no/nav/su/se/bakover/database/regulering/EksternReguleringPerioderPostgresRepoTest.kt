package no.nav.su.se.bakover.database.regulering

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.regulering.EksternKilde
import no.nav.su.se.bakover.domain.regulering.EksternPeriode
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioder
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.time.LocalDate
import java.util.UUID

internal class EksternReguleringPerioderPostgresRepoTest {
    @Test
    fun `lagrer og henter eksterne perioder for to saker`() {
        withMigratedDb { dataSource ->
            val repo = TestDataHelper(dataSource).eksternReguleringPerioderRepo
            val kjøringId = UUID.randomUUID()

            val sak1 = EksternReguleringPerioder(
                kjøringId = kjøringId,
                saksnummer = Saksnummer(2021),
                tilhører = FradragTilhører.BRUKER,
                eksternKilde = EksternKilde.PESYS,
                perioder = listOf(
                    EksternPeriode(
                        fom = LocalDate.of(2026, 5, 1),
                        tom = LocalDate.of(2026, 12, 31),
                        grunnbeløp = 130160,
                        netto = 25000,
                        inntektEtterUføre = 100000,
                    ),
                ),
            )
            val sak2 = EksternReguleringPerioder(
                kjøringId = kjøringId,
                saksnummer = Saksnummer(2022),
                tilhører = FradragTilhører.EPS,
                eksternKilde = EksternKilde.AAP,
                perioder = listOf(
                    EksternPeriode(
                        fom = LocalDate.of(2026, 5, 1),
                        tom = null,
                        grunnbeløp = 130160,
                        netto = 18000,
                    ),
                ),
            )
            val sakMedFeil = EksternReguleringPerioder(
                kjøringId = kjøringId,
                saksnummer = Saksnummer(2023),
                tilhører = FradragTilhører.BRUKER,
                eksternKilde = EksternKilde.PESYS,
                perioder = emptyList(),
                feilkoder = listOf("GrunnbeløpFraPesysUliktForventetNytt"),
            )

            repo.lagre(listOf(sak1, sak2, sakMedFeil))

            repo.hentForKjøring(kjøringId) shouldContainExactlyInAnyOrder listOf(sak1, sak2, sakMedFeil)
            repo.hentForKjøring(UUID.randomUUID()) shouldBe emptyList()
        }
    }
}
