package no.nav.su.se.bakover.common.periode

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.september
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal class PeriodeTest {
    @Test
    fun `fra og med og til og med`() {
        val periode = januar(2021)
        periode.fraOgMed shouldBe 1.januar(2021)
        periode.tilOgMed shouldBe 31.januar(2021)
    }

    @Test
    fun `periodisert fra og med og til og med`() {
        val periode = år(2021)
        val periodisert = periode.måneder()
        periode.fraOgMed shouldBe 1.januar(2021)
        periode.tilOgMed shouldBe 31.desember(2021)
        periodisert.first().fraOgMed shouldBe 1.januar(2021)
        periodisert.first().tilOgMed shouldBe 31.januar(2021)
        periodisert.last().fraOgMed shouldBe 1.desember(2021)
        periodisert.last().tilOgMed shouldBe 31.desember(2021)
    }

    @Test
    fun `periodiserer måneder`() {
        val periode = januar(2021)
        val periodisert = periode.måneder()
        periodisert shouldBe listOf(januar(2021))
    }

    @Test
    fun `periodiserer flere måneder`() {
        val periode = Periode.create(1.januar(2021), 30.april(2021))
        val periodisert = periode.måneder()
        periodisert shouldBe listOf(
            januar(2021),
            februar(2021),
            mars(2021),
            april(2021),
        )
        periodisert shouldHaveSize periode.getAntallMåneder()
    }

    @Test
    fun `får ikke opprettet perioder med ugyldige input parametere `() {
        assertThrows<IllegalArgumentException> { Periode.create(10.januar(2021), 31.desember(2021)) }
        assertThrows<IllegalArgumentException> { Periode.create(1.januar(2021), 10.desember(2021)) }
        assertThrows<IllegalArgumentException> { Periode.create(1.februar(2021), 31.januar(2021)) }
    }

    @Test
    fun `får ikke opprettet perioder med ugyldige input parametere trycreate`() {
        Periode.tryCreate(
            10.januar(2021),
            31.desember(2021),
        ) shouldBe Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden.left()
        Periode.tryCreate(
            1.februar(2021),
            31.januar(2021),
        ) shouldBe Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato.left()
        Periode.tryCreate(
            1.januar(2021),
            30.desember(2021),
        ) shouldBe Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden.left()
    }

    @Test
    fun `periode kan ikke være mer enn 12 måneder`() {
        shouldThrowExactly<IllegalArgumentException> { Periode.create(1.januar(2021), 1.januar(2022)) }
    }

    @Test
    fun `periode kan være 12 måneder`() {
        år(2021)
    }

    @Test
    fun `periode kan være mindre enn 12 måneder`() {
        (1..12).forEach {
            Periode.create(1.januar(2021), LocalDate.of(2021, it, 1).with(TemporalAdjusters.lastDayOfMonth()))
        }
    }

    @Test
    fun `tilstøtende perioder`() {
        år(2021) tilstøter januar(2021) shouldBe false
        år(2021) tilstøter år(2022) shouldBe true
        år(2021) tilstøter år(2022) shouldBe true
        år(2021) tilstøter år(2050) shouldBe false
        år(2025) tilstøter Periode.create(
            1.januar(2024),
            30.november(2024),
        ) shouldBe false
        Periode.create(1.januar(2021), 31.mars(2021)) tilstøter Periode.create(
            1.mai(2021),
            30.november(2021),
        ) shouldBe false
        Periode.create(1.januar(2021), 31.mars(2021)) tilstøter Periode.create(
            1.april(2021),
            30.november(2021),
        ) shouldBe true
        januar(2021) tilstøter januar(2021) shouldBe true
    }

    @Test
    fun `overlapper enkelt periode`() {
        år(2021) overlapper
            år(2021) shouldBe true

        år(2021) overlapper
            Periode.create(1.juli(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.juli(2021), 31.desember(2021)) overlapper
            år(2021) shouldBe true

        år(2021) overlapper
            Periode.create(1.juli(2021), 30.november(2021)) shouldBe true

        Periode.create(1.juli(2021), 30.november(2021)) overlapper
            år(2021) shouldBe true

        år(2021) overlapper
            Periode.create(1.desember(2020), 31.januar(2021)) shouldBe true

        Periode.create(1.desember(2020), 31.januar(2021)) overlapper
            år(2021) shouldBe true

        år(2021) overlapper
            Periode.create(1.desember(2021), 31.januar(2022)) shouldBe true

        Periode.create(1.desember(2021), 31.januar(2022)) overlapper
            år(2021) shouldBe true

        år(2021) overlapper
            Periode.create(1.januar(2020), 31.desember(2022)) shouldBe true

        år(2021) overlapper
            Periode.create(1.januar(2020), 31.desember(2022)) shouldBe true

        år(2021) overlapper
            år(2020) shouldBe false

        år(2020) overlapper
            år(2021) shouldBe false
    }

    @Test
    fun `overlapper fler perioder`() {
        år(2021) fullstendigOverlapp
            listOf(år(2021)) shouldBe true

        år(2021) fullstendigOverlapp
            listOf(
                januar(2021),
                februar(2021),
                mars(2021),
                april(2021),
                mai(2021),
                juni(2021),
                juli(2021),
                august(2021),
                september(2021),
                oktober(2021),
                november(2021),
                desember(2021),
            ) shouldBe true

        år(2021) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe true

        år(2021) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe true

        år(2021) fullstendigOverlapp
            listOf(
                Periode.create(1.februar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        år(2021) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 30.november(2021)),
            ) shouldBe false

        år(2021) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 31.mai(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        Periode.create(1.februar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        Periode.create(1.januar(2021), 30.november(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        Periode.create(1.januar(2021), 30.november(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        år(2021) fullstendigOverlapp
            listOf(
                år(2021),
                år(2021),
                år(2021),
            ) shouldBe true
    }

    @Test
    fun `bevarer original periode dersom maks inneholder original`() {
        år(2021) snitt juli(2021) shouldBe juli(2021)
        juli(2021) snitt år(2021) shouldBe juli(2021)
    }

    @Test
    fun `justerer original periode dersom original inneholder maks`() {
        juli(2021) snitt år(2021) shouldBe juli(2021)
        år(2021) snitt juli(2021) shouldBe juli(2021)
    }

    @Test
    fun `returnerer ingenting hvis det ikke er overlapp mellom maks og original`() {
        juli(2021) snitt desember(2021) shouldBe null
        desember(2021) snitt juli(2021) shouldBe null
    }

    @Test
    fun `justerer fraOgMed hvis original starter før maks`() {
        Periode.create(1.juli(2021), 31.desember(2021)) snitt år(2021) shouldBe Periode.create(1.juli(2021), 31.desember(2021))
        år(2021) snitt Periode.create(
            1.juli(2021),
            31.desember(2021),
        ) shouldBe Periode.create(1.juli(2021), 31.desember(2021))
    }

    @Test
    fun `justerer tilOgMed hvis original slutter etter maks`() {
        Periode.create(1.januar(2021), 31.juli(2021)) snitt år(2021) shouldBe Periode.create(1.januar(2021), 31.juli(2021))
        år(2021) snitt Periode.create(
            1.januar(2021),
            31.juli(2021),
        ) shouldBe Periode.create(1.januar(2021), 31.juli(2021))
    }

    @Test
    fun `justerer fraOgMed hvis original starter før og slutter før maks`() {
        Periode.create(1.juli(2021), 31.desember(2021)) snitt Periode.create(
            1.januar(2021),
            31.oktober(2021),
        ) shouldBe Periode.create(1.juli(2021), 31.oktober(2021))
        Periode.create(1.januar(2021), 31.oktober(2021)) snitt Periode.create(
            1.juli(2021),
            31.desember(2021),
        ) shouldBe Periode.create(1.juli(2021), 31.oktober(2021))
    }

    @Test
    fun `justerer tilOgMed hvis original starter seneere enn og slutter etter maks`() {
        Periode.create(1.januar(2021), 31.juli(2021)) snitt Periode.create(
            1.mars(2021),
            31.desember(2021),
        ) shouldBe Periode.create(1.mars(2021), 31.juli(2021))
        Periode.create(1.mars(2021), 31.desember(2021)) snitt Periode.create(
            1.januar(2021),
            31.juli(2021),
        ) shouldBe Periode.create(1.mars(2021), 31.juli(2021))
    }

    @Test
    fun `starter samtidig`() {
        år(2021) starterSamtidig
            år(2021) shouldBe true

        år(2021) starterSamtidig
            Periode.create(1.februar(2021), 31.desember(2021)) shouldBe false
    }

    @Test
    fun `starter tidligere`() {
        år(2021) starterTidligere
            år(2021) shouldBe false

        år(2021) starterTidligere
            Periode.create(1.februar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.februar(2021), 31.desember(2021)) starterTidligere
            år(2021) shouldBe false
    }

    @Test
    fun `starter etter`() {
        år(2021) starterEtter
            år(2021) shouldBe false

        år(2021) starterEtter
            Periode.create(1.februar(2021), 31.desember(2021)) shouldBe false

        Periode.create(1.februar(2021), 31.desember(2021)) starterEtter
            år(2021) shouldBe true
    }

    @Test
    fun `slutter samtidig`() {
        år(2021) slutterSamtidig
            år(2021) shouldBe true

        Periode.create(1.januar(2021), 30.november(2021)) slutterSamtidig
            år(2021) shouldBe false
    }

    @Test
    fun `slutter tidligere`() {
        år(2021) slutterTidligere
            år(2021) shouldBe false

        Periode.create(1.januar(2021), 30.november(2021)) slutterTidligere
            Periode.create(1.februar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.februar(2021), 31.desember(2021)) slutterTidligere
            Periode.create(1.januar(2021), 30.november(2021)) shouldBe false
    }

    @Test
    fun før() {
        Periode.create(1.januar(2021), 31.juli(2021)) før
            desember(2021) shouldBe true

        Periode.create(1.januar(2021), 30.november(2021)) før
            år(2021) shouldBe false

        desember(2021) før
            november(2021) shouldBe false
    }

    @Test
    fun etter() {
        Periode.create(1.januar(2021), 31.juli(2021)) etter
            desember(2021) shouldBe false

        Periode.create(1.januar(2021), 30.november(2021)) etter
            år(2021) shouldBe false

        desember(2021) etter
            november(2021) shouldBe true
    }

    @Test
    fun `starter samtidig eller senere`() {
        år(2021) starterSamtidigEllerSenere
            år(2021) shouldBe true

        Periode.create(1.februar(2021), 31.desember(2021)) starterSamtidigEllerSenere
            år(2021) shouldBe true

        år(2021) starterSamtidigEllerSenere
            desember(2021) shouldBe false
    }

    @Test
    fun `starter samtidig eller tidligere`() {
        år(2021) starterSamtidigEllerTidligere
            år(2021) shouldBe true

        Periode.create(1.februar(2021), 31.desember(2021)) starterSamtidigEllerTidligere
            år(2021) shouldBe false

        år(2021) starterSamtidigEllerTidligere
            desember(2021) shouldBe true
    }

    @Test
    fun `slutter samtidig eller tidligere`() {
        år(2021) slutterSamtidigEllerTidligere
            år(2021) shouldBe true

        Periode.create(1.februar(2021), 30.november(2021)) slutterSamtidigEllerTidligere
            år(2021) shouldBe true

        år(2021) slutterSamtidigEllerTidligere
            november(2021) shouldBe false
    }

    @Test
    fun `slutter samtidig eller senere`() {
        år(2021) slutterSamtidigEllerSenere
            år(2021) shouldBe true

        Periode.create(1.februar(2021), 30.november(2021)) slutterSamtidigEllerSenere
            år(2021) shouldBe false

        år(2021) slutterSamtidigEllerSenere
            november(2021) shouldBe true
    }

    @Test
    fun `slutter inni`() {
        år(2021) slutterInni
            år(2021) shouldBe true

        Periode.create(1.januar(2021), 30.november(2021)) slutterInni
            år(2021) shouldBe true

        år(2021) slutterInni
            Periode.create(1.januar(2021), 30.november(2021)) shouldBe false

        Periode.create(1.januar(2021), 31.mai(2021)) slutterInni
            Periode.create(1.juli(2021), 30.november(2021)) shouldBe false
    }

    @Test
    fun `serialisering av periode`() {
        val expectedJson = """
            {
                "fraOgMed":"2021-01-01",
                "tilOgMed":"2021-12-31"
            }
        """.trimIndent()

        val serialized = objectMapper.writeValueAsString(år(2021))

        JSONAssert.assertEquals(expectedJson, serialized, true)
    }

    @Test
    fun `deserialisering av periode`() {
        val serialized = """
            {
                "fraOgMed":"2021-01-01",
                "tilOgMed":"2021-12-31"
            }
        """.trimIndent()

        val deserialized = objectMapper.readValue<Periode>(serialized)

        deserialized shouldBe år(2021)
    }

    @Test
    fun `slår sammen perioder hvis mulig`() {
        januar(2021) slåSammen
            februar(2021) shouldBe (
            Periode.create(
                1.januar(2021),
                28.februar(2021),
            )
            ).right()

        Periode.create(1.januar(2021), 31.juli(2021)) slåSammen
            februar(2021) shouldBe (
            Periode.create(
                1.januar(2021),
                31.juli(2021),
            )
            ).right()

        Periode.create(1.september(2021), 31.desember(2021)) slåSammen
            Periode.create(1.mars(2021), 31.oktober(2021)) shouldBe (
            Periode.create(
                1.mars(2021),
                31.desember(2021),
            )
            ).right()

        mars(2021) slåSammen
            april(2021) shouldBe (
            Periode.create(
                1.mars(2021),
                30.april(2021),
            )
            ).right()

        Periode.create(1.juni(2021), 31.desember(2021)) slåSammen
            Periode.create(1.mars(2021), 31.mai(2021)) shouldBe (
            Periode.create(
                1.mars(2021),
                31.desember(2021),
            )
            ).right()

        Periode.create(1.juni(2021), 31.juli(2021)) slåSammen
            Periode.create(1.oktober(2021), 31.desember(2021)) shouldBe Periode.PerioderKanIkkeSlåsSammen.left()
    }

    @Test
    fun `reduserer perioder`() {
        listOf(
            januar(2021),
            februar(2021),
            mars(2021),
            april(2021),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.januar(2021), 30.april(2021)),
        )

        listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.februar(2021), 30.april(2021)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.januar(2021), 30.april(2021)),
        )

        listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.mars(2021), 31.desember(2021)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            år(2021),
        )

        listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
            Periode.create(1.februar(2021), 31.mars(2021)),
            Periode.create(1.februar(2021), 31.desember(2021)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.februar(2021), 31.desember(2021)),
        )

        listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
            Periode.create(1.februar(2022), 31.mars(2022)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
            Periode.create(1.februar(2022), 31.mars(2022)),
        )

        listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
        )

        emptyList<Periode>().minsteAntallSammenhengendePerioder() shouldBe emptyList()

        listOf(
            Periode.create(1.mai(2022), 31.august(2022)),
            Periode.create(1.januar(2023), 30.april(2023)),
            Periode.create(1.september(2022), 31.desember(2022)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(Periode.create(1.mai(2022), 30.april(2023)))
    }

    @Test
    fun `forskyvning av perioder`() {
        januar(2021).forskyv(1) shouldBe februar(2021)
        januar(2021).forskyv(-1) shouldBe desember(2020)
        januar(2021).forskyv(12) shouldBe januar(2022)
        januar(2021).forskyv(-12) shouldBe januar(2020)

        år(2021).forskyv(1) shouldBe
            Periode.create(1.februar(2021), 31.januar(2022))
        år(2021).forskyv(-1) shouldBe
            Periode.create(1.desember(2020), 30.november(2021))
        år(2021).forskyv(12) shouldBe
            år(2022)
        år(2021).forskyv(-12) shouldBe
            år(2020)
    }

    @Test
    fun `minus`() {
        januar(2021) minus februar(2021) shouldBe listOf(januar(2021))
        januar(2021) minus januar(2021) shouldBe emptyList()
        år(2021) minus juni(2021) shouldBe listOf(
            Periode.create(1.januar(2021), 31.mai(2021)),
            Periode.create(1.juli(2021), 31.desember(2021)),
        )
        år(2021) minus
            Periode.create(1.mars(2021), 31.juli(2021)) shouldBe listOf(
            Periode.create(1.januar(2021), 28.februar(2021)),
            Periode.create(1.august(2021), 31.desember(2021)),
        )
    }

    @Test
    fun `minus liste`() {
        listOf(
            januar(2021),
            februar(2021),
            mars(2021),
        ).minus(listOf(januar(2021))) shouldBe listOf(
            Periode.create(1.februar(2021), 31.mars(2021)),
        )

        listOf(
            januar(2021),
            februar(2021),
            mars(2021),
        ).minus(listOf(februar(2021))) shouldBe listOf(
            januar(2021),
            mars(2021),
        )

        listOf(
            år(2021),
            Periode.create(1.mars(2022), 31.mai(2022)),
        ).minus(
            listOf(
                Periode.create(1.april(2021), 31.august(2021)),
                januar(2022),
                februar(2022),
                mars(2022),
            ),
        ) shouldBe listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.september(2021), 31.desember(2021)),
            Periode.create(1.april(2022), 31.mai(2022)),
        )
    }

    @Test
    fun `overlappende`() {
        listOf(
            januar(2021),
            januar(2021),
        ).harOverlappende() shouldBe true

        listOf(
            januar(2021),
            februar(2021),
        ).harOverlappende() shouldBe false

        listOf<Periode>().harOverlappende() shouldBe false

        listOf(
            mai(2021),
            år(2021),
        ).harOverlappende() shouldBe true

        listOf(
            mai(2021),
            år(2021),
            år(2022),
        ).harOverlappende() shouldBe true
    }

    @Nested
    inner class `liste av perioder til måneder()` {

        @Test
        fun `månedsperiode forblir månedsperioder`() {
            listOf(
                januar(2021),
                februar(2021),
                mars(2021),
            ).måneder() shouldBe listOf(
                januar(2021),
                februar(2021),
                mars(2021),
            )
        }

        @Test
        fun `sorterte perioder til sorterte månedsperioder`() {
            nonEmptyListOf(
                Periode.create(1.januar(2021), 28.februar(2021)),
                Periode.create(1.mars(2021), 30.april(2021)),
            ).måneder() shouldBe listOf(
                januar(2021),
                februar(2021),
                mars(2021),
                april(2021),
            )
        }

        @Test
        fun `duplikate perioder til ikke-duplikate-månedsperioder`() {
            listOf(
                Periode.create(1.januar(2021), 28.februar(2021)),
                Periode.create(1.januar(2021), 28.februar(2021)),
            ).måneder() shouldBe listOf(
                januar(2021),
                februar(2021),
            )
        }

        @Test
        fun `usorterte perioder til sorterte månedsperioder`() {
            nonEmptyListOf(
                Periode.create(1.mars(2021), 30.april(2021)),
                Periode.create(1.januar(2021), 28.februar(2021)),
            ).måneder() shouldBe listOf(
                januar(2021),
                februar(2021),
                mars(2021),
                april(2021),
            )
        }

        @Test
        fun `usorterte duplikate ikke-sammenhengde perioder til sorterte unike ikke-sammenhengende månedsperioder`() {
            listOf(
                Periode.create(1.april(2021), 31.mai(2021)),
                Periode.create(1.januar(2021), 28.februar(2021)),
                Periode.create(1.april(2021), 31.mai(2021)),
                Periode.create(1.januar(2021), 28.februar(2021)),
            ).måneder() shouldBe listOf(
                januar(2021),
                februar(2021),
                april(2021),
                mai(2021),
            )
        }
    }

    @Nested
    inner class `erSortert()` {

        @Test
        fun `sorter liste en måned`() {
            listOf(
                januar(2021),
            ).erSortert() shouldBe true
        }

        @Test
        fun `sorter liste duplikater`() {
            listOf(
                Periode.create(1.januar(2021), 28.februar(2021)),
                Periode.create(1.januar(2021), 28.februar(2021)),
            ).erSortert() shouldBe true
        }

        @Test
        fun `sorter liste usammenhengende`() {
            listOf(
                Periode.create(1.januar(2021), 28.februar(2021)),
                Periode.create(1.april(2021), 31.mai(2021)),
            ).erSortert() shouldBe true
        }

        @Test
        fun `ikke-sorter liste usammenhengende`() {
            listOf(
                Periode.create(1.april(2021), 31.mai(2021)),
                Periode.create(1.januar(2021), 28.februar(2021)),
            ).erSortert() shouldBe false
        }

        @Test
        fun `ikke-sortert liste sammenhengende`() {
            listOf(
                februar(2021),
                januar(2021),
            ).erSortert() shouldBe false
        }

        @Test
        fun `ikke-sortert liste duplikater`() {
            listOf(
                februar(2021),
                januar(2021),
                februar(2021),
            ).erSortert() shouldBe false
        }
    }

    @Nested
    inner class `harDuplikater()` {
        @Test
        fun `usortert med to like måneder er duplikat`() {
            listOf(
                februar(2021),
                januar(2021),
                februar(2021),
            ).harDuplikater() shouldBe true
        }

        @Test
        fun `to like måneder er duplikat`() {
            listOf(
                februar(2021),
                februar(2021),
            ).harDuplikater() shouldBe true
        }

        @Test
        fun `enkel måned har ikke duplikater`() {
            listOf(
                februar(2021),
            ).harDuplikater() shouldBe false
        }

        @Test
        fun `enkel periode har ikke duplikater`() {
            listOf(
                år(2021),
            ).harDuplikater() shouldBe false
        }

        @Test
        fun `flere tilstøtende perioder har ikke duplikater`() {
            listOf(
                år(2021),
                år(2023),
            ).harDuplikater() shouldBe false
        }

        @Test
        fun `flere ikke-sammenhengende perioder har ikke duplikater`() {
            listOf(
                år(2021),
                år(2023),
            ).harDuplikater() shouldBe false
        }

        @Test
        fun `to like perioder har duplikater`() {
            listOf(
                år(2021),
                år(2021),
            ).harDuplikater() shouldBe true
        }

        @Test
        fun `perioder med overlapp har dupliakter`() {
            listOf(
                Periode.create(1.januar(2021), 31.mars(2021)),
                Periode.create(1.mars(2021), 31.juli(2021)),
            ).harDuplikater() shouldBe true
        }
    }

    @Nested
    inner class `erSammenhengende()` {
        @Test
        fun `enkel måned er sammenhengende`() {
            listOf(
                januar(2021),
            ).erSammenhengende() shouldBe true
        }

        @Test
        fun `enkel duplikat måned er sammenhengende`() {
            listOf(
                januar(2021),
                januar(2021),
            ).erSammenhengende() shouldBe true
        }

        @Test
        fun `enkel periode er sammenhengende`() {
            listOf(
                år(2021),
            ).erSammenhengende() shouldBe true
        }

        @Test
        fun `enkel duplikat periode er sammenhengende`() {
            listOf(
                år(2021),
                år(2021),
            ).erSammenhengende() shouldBe true
        }

        @Test
        fun `tilstøtende måneder er sammenhengende`() {
            listOf(
                januar(2021),
                februar(2021),
            ).erSammenhengende() shouldBe true
        }

        @Test
        fun `tilstøtende år er sammenhengende`() {
            listOf(
                år(2021),
                år(2022),
            ).erSammenhengende() shouldBe true
        }

        @Test
        fun `ikke-tilstøtende måneder er ikke sammenhengende`() {
            listOf(
                januar(2021),
                mars(2021),
            ).erSammenhengende() shouldBe false
        }

        @Test
        fun `ikke-tilstøtende perioder er ikke sammenhengende`() {
            listOf(
                Periode.create(1.januar(2021), 28.februar(2021)),
                Periode.create(1.april(2021), 31.juli(2021)),
            ).erSammenhengende() shouldBe false
        }
    }

    @Nested
    inner class `erSammenhengendeSortertOgUtenDuplikater()` {
        @Test
        fun `enkel måned gir true`() {
            listOf(
                januar(2021),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe true
        }

        @Test
        fun `enkel periode gir true`() {
            listOf(
                Periode.create(1.april(2021), 31.juli(2021)),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe true
        }

        @Test
        fun `tilstøtende måneder gir true`() {
            listOf(
                januar(2021),
                februar(2021),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe true
        }

        @Test
        fun `tilstøtende perioder gir true`() {
            listOf(
                år(2021),
                år(2022),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe true
        }

        @Test
        fun `usortert måneder gir false`() {
            listOf(
                februar(2021),
                januar(2021),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe false
        }

        @Test
        fun `usortert perioder gir false`() {
            listOf(
                år(2022),
                år(2021),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe false
        }

        @Test
        fun `ikke-sammenhengende måneder gir false`() {
            listOf(
                januar(2021),
                mars(2021),
            ).associateWith { Any() }.erSammenhengendeSortertOgUtenDuplikater() shouldBe false
        }

        @Test
        fun `ikke-sammenhengende perioder gir false`() {
            listOf(
                år(2021),
                år(2023),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe false
        }

        @Test
        fun `duplikat måned gir false`() {
            listOf(
                januar(2021),
                januar(2021),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe false
        }

        @Test
        fun `duplikat periode gir false`() {
            listOf(
                år(2021),
                år(2022),
                år(2022),
            ).erSammenhengendeSortertOgUtenDuplikater() shouldBe false
        }
    }

    @Nested
    inner class Komplement {
        @Test
        fun `komplement av tom liste er tom`() {
            emptyList<Periode>().komplement() shouldBe emptyList()
        }

        @Test
        fun `komplement av en er en tom liste`() {
            listOf(år(2021)).komplement() shouldBe emptyList()
        }

        @Test
        fun `komplement av tilstøtende er tom liste`() {
            listOf(år(2021), år(2022)).komplement() shouldBe emptyList()
        }

        @Test
        fun `komplement av distinkte med hull`() {
            listOf(år(2021), år(2023)).komplement() shouldBe listOf(år(2022))
        }

        @Test
        fun `komplement av overlappende uten hull er tom liste`() {
            listOf(
                januar(2022).rangeTo(april(2022)),
                februar((2022)).rangeTo(desember(2022)),
            ).komplement() shouldBe emptyList()
        }

        @Test
        fun `komplement av overlappende med hull`() {
            listOf(
                januar(2022).rangeTo(april(2022)),
                februar((2022)).rangeTo(mai(2022)),
                juli(2022).rangeTo(september(2022)),
                juli((2022)).rangeTo(desember(2022)),
            ).komplement() shouldBe listOf(juni(2022))
        }
    }
}
