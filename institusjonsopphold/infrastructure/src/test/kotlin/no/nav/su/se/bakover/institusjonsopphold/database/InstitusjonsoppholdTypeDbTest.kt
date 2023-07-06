package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonsoppholdType
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdTypeDb.Companion.toDb
import org.junit.jupiter.api.Test

class InstitusjonsoppholdTypeDbTest {

    @Test
    fun `mapper domenet type til riktig db type`() {
        InstitusjonsoppholdType.OPPDATERING.toDb() shouldBe InstitusjonsoppholdTypeDb.OPPDATERING
        InstitusjonsoppholdType.ANNULERING.toDb() shouldBe InstitusjonsoppholdTypeDb.ANNULERING
        InstitusjonsoppholdType.INNMELDING.toDb() shouldBe InstitusjonsoppholdTypeDb.INNMELDING
        InstitusjonsoppholdType.UTMELDING.toDb() shouldBe InstitusjonsoppholdTypeDb.UTMELDING
    }

    @Test
    fun `mapper db type til riktig domene type`() {
        InstitusjonsoppholdTypeDb.OPPDATERING.toDomain() shouldBe InstitusjonsoppholdType.OPPDATERING
        InstitusjonsoppholdTypeDb.ANNULERING.toDomain() shouldBe InstitusjonsoppholdType.ANNULERING
        InstitusjonsoppholdTypeDb.INNMELDING.toDomain() shouldBe InstitusjonsoppholdType.INNMELDING
        InstitusjonsoppholdTypeDb.UTMELDING.toDomain() shouldBe InstitusjonsoppholdType.UTMELDING
    }
}
