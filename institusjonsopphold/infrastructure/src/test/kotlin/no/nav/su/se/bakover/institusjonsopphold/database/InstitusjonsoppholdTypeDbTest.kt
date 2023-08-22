package no.nav.su.se.bakover.institusjonsopphold.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonsoppholdType
import no.nav.su.se.bakover.institusjonsopphold.database.InstitusjonsoppholdTypeDb.Companion.toJson
import org.junit.jupiter.api.Test

class InstitusjonsoppholdTypeDbTest {

    @Test
    fun `mapper domenet type til riktig db type`() {
        InstitusjonsoppholdType.OPPDATERING.toJson() shouldBe InstitusjonsoppholdTypeDb.OPPDATERING
        InstitusjonsoppholdType.ANNULERING.toJson() shouldBe InstitusjonsoppholdTypeDb.ANNULERING
        InstitusjonsoppholdType.INNMELDING.toJson() shouldBe InstitusjonsoppholdTypeDb.INNMELDING
        InstitusjonsoppholdType.UTMELDING.toJson() shouldBe InstitusjonsoppholdTypeDb.UTMELDING
    }

    @Test
    fun `mapper db type til riktig domene type`() {
        InstitusjonsoppholdTypeDb.OPPDATERING.toDomain() shouldBe InstitusjonsoppholdType.OPPDATERING
        InstitusjonsoppholdTypeDb.ANNULERING.toDomain() shouldBe InstitusjonsoppholdType.ANNULERING
        InstitusjonsoppholdTypeDb.INNMELDING.toDomain() shouldBe InstitusjonsoppholdType.INNMELDING
        InstitusjonsoppholdTypeDb.UTMELDING.toDomain() shouldBe InstitusjonsoppholdType.UTMELDING
    }
}
