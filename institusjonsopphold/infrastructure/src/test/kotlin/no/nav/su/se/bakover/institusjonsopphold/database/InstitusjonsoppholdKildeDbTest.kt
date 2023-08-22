package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdKildeDb.Companion.toJson
import org.junit.jupiter.api.Test

class InstitusjonsoppholdKildeDbTest {

    @Test
    fun `mapper domenet type til riktig db type`() {
        InstitusjonsoppholdKilde.Institusjon.toJson() shouldBe InstitusjonsoppholdKildeDb.INST
        InstitusjonsoppholdKilde.Infotrygd.toJson() shouldBe InstitusjonsoppholdKildeDb.IT
        InstitusjonsoppholdKilde.Kriminalomsorgsdirektoratet.toJson() shouldBe InstitusjonsoppholdKildeDb.KDI
        InstitusjonsoppholdKilde.Applikasjonsbruker.toJson() shouldBe InstitusjonsoppholdKildeDb.APPBRK
    }

    @Test
    fun `mapper db type til riktig domene type`() {
        InstitusjonsoppholdKildeDb.INST.toDomain() shouldBe InstitusjonsoppholdKilde.Institusjon
        InstitusjonsoppholdKildeDb.IT.toDomain() shouldBe InstitusjonsoppholdKilde.Infotrygd
        InstitusjonsoppholdKildeDb.KDI.toDomain() shouldBe InstitusjonsoppholdKilde.Kriminalomsorgsdirektoratet
        InstitusjonsoppholdKildeDb.APPBRK.toDomain() shouldBe InstitusjonsoppholdKilde.Applikasjonsbruker
    }
}
