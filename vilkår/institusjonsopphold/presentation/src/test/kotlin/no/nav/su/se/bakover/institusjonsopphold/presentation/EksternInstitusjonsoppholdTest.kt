package no.nav.su.se.bakover.institusjonsopphold.presentation

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.InstitusjonsoppholdKilde
import no.nav.su.se.bakover.domain.InstitusjonsoppholdType
import org.junit.jupiter.api.Test

class EksternInstitusjonsoppholdTest {

    @Test
    fun `mapper type til domain`() {
        EksternInstitusjonsoppholdTypeJson.INNMELDING.toDomain() shouldBe InstitusjonsoppholdType.INNMELDING
        EksternInstitusjonsoppholdTypeJson.OPPDATERING.toDomain() shouldBe InstitusjonsoppholdType.OPPDATERING
        EksternInstitusjonsoppholdTypeJson.ANNULERING.toDomain() shouldBe InstitusjonsoppholdType.ANNULERING
        EksternInstitusjonsoppholdTypeJson.UTMELDING.toDomain() shouldBe InstitusjonsoppholdType.UTMELDING
    }

    @Test
    fun `mapper kilde til domain`() {
        EksternInstitusjonsoppholdKildeJson.INST.toDomain() shouldBe InstitusjonsoppholdKilde.Institusjon
        EksternInstitusjonsoppholdKildeJson.APPBRK.toDomain() shouldBe InstitusjonsoppholdKilde.Applikasjonsbruker
        EksternInstitusjonsoppholdKildeJson.IT.toDomain() shouldBe InstitusjonsoppholdKilde.Infotrygd
        EksternInstitusjonsoppholdKildeJson.KDI.toDomain() shouldBe InstitusjonsoppholdKilde.Kriminalomsorgsdirektoratet
    }
}
