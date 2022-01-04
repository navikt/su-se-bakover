package no.nav.su.se.bakover.database.klage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.util.UUID

internal class KlagevedtakPostgresRepoTest {

    @Test
    fun `kan opprette uprosessert klagevedtak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klagevedtakRepo = testDataHelper.klagevedtakPostgresRepo
            val uprosessertFattetKlagevedtak = UprosessertFattetKlagevedtak(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                metadata = UprosessertFattetKlagevedtak.Metadata(
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 1,
                    partisjon = 2,
                    key = UUID.randomUUID().toString(),
                    value = "{}",
                ),
            ).also {
                klagevedtakRepo.lagre(it)
            }
            klagevedtakRepo.hentUbehandlaKlagevedtak() shouldBe listOf(uprosessertFattetKlagevedtak)
        }
    }

    @Test
    fun `Dedup på metadata's hendelseId`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klagevedtakRepo = testDataHelper.klagevedtakPostgresRepo
            val uprosessertFattetKlagevedtak = UprosessertFattetKlagevedtak(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                metadata = UprosessertFattetKlagevedtak.Metadata(
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 1,
                    partisjon = 2,
                    key = UUID.randomUUID().toString(),
                    value = "{}",
                ),
            ).also {
                klagevedtakRepo.lagre(it)
                klagevedtakRepo.lagre(it.copy(id = UUID.randomUUID()))
            }
            klagevedtakRepo.hentUbehandlaKlagevedtak() shouldBe listOf(uprosessertFattetKlagevedtak)
        }
    }

    @Test
    fun `Konflikt på duplikat id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klagevedtakRepo = testDataHelper.klagevedtakPostgresRepo
            UprosessertFattetKlagevedtak(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                metadata = UprosessertFattetKlagevedtak.Metadata(
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 1,
                    partisjon = 2,
                    key = UUID.randomUUID().toString(),
                    value = "{}",
                ),
            ).also {
                klagevedtakRepo.lagre(it)
                shouldThrow<PSQLException> {
                    klagevedtakRepo.lagre(it)
                }.message shouldContain "duplicate key value violates unique constraint \"klagevedtak_pkey\""
            }
        }
    }

    @Test
    fun `Endrer og lagrer type til PROSESSERT`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klagevedtakRepo = testDataHelper.klagevedtakPostgresRepo
            val id = UUID.randomUUID()
            UprosessertFattetKlagevedtak(
                id = id,
                opprettet = fixedTidspunkt,
                metadata = UprosessertFattetKlagevedtak.Metadata(
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 1,
                    partisjon = 2,
                    key = UUID.randomUUID().toString(),
                    value = "{}",
                ),
            ).also {
                klagevedtakRepo.lagre(it)
                klagevedtakRepo.markerSomProssesert(it.id)
                klagevedtakRepo.hentUbehandlaKlagevedtak() shouldBe emptyList()
            }
        }
    }

}
