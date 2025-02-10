package no.nav.su.se.bakover.dokument.infrastructure.database

import dokument.domain.Dokument
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

class DokumentHendelsePostgresRepoTest {

    @Test
    fun hentVedtaksbrevdatoForSakOgVedtakId() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            val (sak, _, _, vedtak, hendelser) = testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            // TODO jah: Ikke ideelt og måtte bygge opp dette innholdet selv.
            val relaterteHendelse = hendelser.last().hendelseId
            testDataHelper.dokumentHendelse.persisterDokumentHendelse(
                sakId = sak.id,
                relaterteHendelse = relaterteHendelse,
                generertDokumentJson = """{
                     "personalia": {
                       "dato": "01.02.2021"
                     }
                    }
                """.trimIndent(),
                dokumentMedMetadata = Dokument.Metadata(
                    sakId = sak.id,
                    vedtakId = vedtak.id,
                ),
            )
            testDataHelper.dokumentHendelseRepo.hentVedtaksbrevdatoForSakOgVedtakId(
                sakId = sak.id,
                vedtakId = vedtak.id,
            ) shouldBe 1.februar(2021)
        }
    }

    @Test
    fun hentHendelseForDokumentId() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            val (sak, _, _, vedtak, hendelser) = testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            // TODO jah: Ikke ideelt og måtte bygge opp dette innholdet selv.
            val relaterteHendelse = hendelser.last().hendelseId
            val dokumentMedMetadata = Dokument.Metadata(
                sakId = sak.id,
                vedtakId = vedtak.id,
            )
            val (generertDokumentHendelse, _, _) = testDataHelper.dokumentHendelse.persisterDokumentHendelse(
                sakId = sak.id,
                relaterteHendelse = relaterteHendelse,
                generertDokumentJson = """{
                     "personalia": {
                       "dato": "2021-02-01"
                     }
                    }
                """.trimIndent(),
                dokumentMedMetadata = dokumentMedMetadata,
            )
            testDataHelper.dokumentHendelseRepo.hentHendelseForDokumentId(
                dokumentId = generertDokumentHendelse.dokumentId,
            ) shouldBe generertDokumentHendelse
        }
    }

    @Test
    fun hentDokumentMedMetadataForSakId() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            val (sak, _, _, vedtak, hendelser) = testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            // TODO jah: Ikke ideelt og måtte bygge opp persisterDokumentHendelse manuelt i hver test.
            val relaterteHendelse = hendelser.last().hendelseId
            val dokumentMedMetadata = Dokument.Metadata(
                sakId = sak.id,
                vedtakId = vedtak.id,
            )
            val generertDokumentJson = """{
                     "personalia": {
                       "dato": "2021-02-01"
                     }
                    }
            """.trimIndent()
            val (generertDokumentHendelse, hendelseFil, _) = testDataHelper.dokumentHendelse.persisterDokumentHendelse(
                sakId = sak.id,
                relaterteHendelse = relaterteHendelse,
                generertDokumentJson = generertDokumentJson,
                dokumentMedMetadata = dokumentMedMetadata,
            )
            val actual = testDataHelper.dokumentHendelseRepo.hentDokumentMedMetadataForSakId(
                sakId = sak.id,
            )
            actual shouldBe listOf(
                Dokument.MedMetadata.Vedtak(
                    id = generertDokumentHendelse.dokumentId,
                    opprettet = Tidspunkt.parse("2021-01-01T01:02:03.456789Z"),
                    tittel = "Dokument-tittel",
                    generertDokument = hendelseFil.fil,
                    generertDokumentJson = generertDokumentJson,
                    metadata = dokumentMedMetadata,
                    distribueringsadresse = null,
                ),
            )
        }
    }

    @Test
    fun hentDokumentMedMetadataForSakIdOgDokumentId() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            val (sak, _, _, vedtak, hendelser) = testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            // TODO jah: Ikke ideelt og måtte bygge opp persisterDokumentHendelse manuelt i hver test.
            val relaterteHendelse = hendelser.last().hendelseId
            val dokumentMedMetadata = Dokument.Metadata(
                sakId = sak.id,
                vedtakId = vedtak.id,
            )
            val generertDokumentJson = """{
                     "personalia": {
                       "dato": "2021-02-01"
                     }
                    }
            """.trimIndent()
            val (generertDokumentHendelse, hendelseFil, _) = testDataHelper.dokumentHendelse.persisterDokumentHendelse(
                sakId = sak.id,
                relaterteHendelse = relaterteHendelse,
                generertDokumentJson = generertDokumentJson,
                dokumentMedMetadata = dokumentMedMetadata,
            )
            val actual = testDataHelper.dokumentHendelseRepo.hentDokumentMedMetadataForSakIdOgDokumentId(
                sakId = sak.id,
                dokumentId = generertDokumentHendelse.dokumentId,
            )!!
            actual shouldBe Dokument.MedMetadata.Vedtak(
                id = generertDokumentHendelse.dokumentId,
                opprettet = Tidspunkt.parse("2021-01-01T01:02:03.456789Z"),
                tittel = "Dokument-tittel",
                generertDokument = hendelseFil.fil,
                generertDokumentJson = generertDokumentJson,
                metadata = dokumentMedMetadata,
                distribueringsadresse = null,
            )
        }
    }

    @Test
    fun hentHendelseOgFilForDokumentId() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            val (sak, _, _, vedtak, hendelser) = testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            // TODO jah: Ikke ideelt og måtte bygge opp persisterDokumentHendelse manuelt i hver test.
            val relaterteHendelse = hendelser.last().hendelseId
            val dokumentMedMetadata = Dokument.Metadata(
                sakId = sak.id,
                vedtakId = vedtak.id,
            )
            val generertDokumentJson = """{
                     "personalia": {
                       "dato": "2021-02-01"
                     }
                    }
            """.trimIndent()
            val (generertDokumentHendelse, hendelseFil, _) = testDataHelper.dokumentHendelse.persisterDokumentHendelse(
                sakId = sak.id,
                relaterteHendelse = relaterteHendelse,
                generertDokumentJson = generertDokumentJson,
                dokumentMedMetadata = dokumentMedMetadata,
            )
            val (actualHendelse, actualFil) = testDataHelper.dokumentHendelseRepo.hentHendelseOgFilForDokumentId(
                dokumentId = generertDokumentHendelse.dokumentId,
            )
            actualHendelse shouldBe generertDokumentHendelse
            actualFil shouldBe hendelseFil
        }
    }
}
