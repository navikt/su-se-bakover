package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.left
import beregning.domain.fradrag.Fradragstype
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class FradragstypeTest {
    @Test
    fun `krever beskrivelse for annet`() {
        Fradragstype.tryParse(
            Fradragstype.Kategori.Annet.name,
            null,
        ) shouldBe Fradragstype.Companion.UgyldigFradragstype.UspesifisertKategoriUtenBeskrivelse.left()
    }

    @Test
    fun `lager annet`() {
        Fradragstype.from(Fradragstype.Kategori.Annet, "ok") shouldBe Fradragstype.Annet("ok")
    }

    @Test
    fun `godtar ikke beskrivelse for spesifisert`() {
        Fradragstype.Kategori.entries.filterNot {
            it == Fradragstype.Kategori.Annet
        }.forEach {
            Fradragstype.tryParse(
                it.name,
                "tull",
            ) shouldBe Fradragstype.Companion.UgyldigFradragstype.SpesifisertKategoriMedBeskrivelse.left()
        }
    }
}
