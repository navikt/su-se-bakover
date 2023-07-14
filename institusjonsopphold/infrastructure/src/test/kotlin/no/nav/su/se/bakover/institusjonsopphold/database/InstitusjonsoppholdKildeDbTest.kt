package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdKildeDb.Companion.toJson
import org.junit.jupiter.api.Test

class InstitusjonsoppholdKildeDbTest {

    @Test
    fun `mapper domenet type til riktig db type`() {
        InstitusjonsoppholdKilde.INST.toJson() shouldBe InstitusjonsoppholdKildeDb.INST
        InstitusjonsoppholdKilde.IT.toJson() shouldBe InstitusjonsoppholdKildeDb.IT
        InstitusjonsoppholdKilde.KDI.toJson() shouldBe InstitusjonsoppholdKildeDb.KDI
        InstitusjonsoppholdKilde.APPBRK.toJson() shouldBe InstitusjonsoppholdKildeDb.APPBRK
    }

    @Test
    fun `mapper db type til riktig domene type`() {
        InstitusjonsoppholdKildeDb.INST.toDomain() shouldBe InstitusjonsoppholdKilde.INST
        InstitusjonsoppholdKildeDb.IT.toDomain() shouldBe InstitusjonsoppholdKilde.IT
        InstitusjonsoppholdKildeDb.KDI.toDomain() shouldBe InstitusjonsoppholdKilde.KDI
        InstitusjonsoppholdKildeDb.APPBRK.toDomain() shouldBe InstitusjonsoppholdKilde.APPBRK
    }
}
