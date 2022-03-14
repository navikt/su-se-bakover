package no.nav.su.se.bakover.database.klage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.util.UUID

internal class KlageinstanshendelsePostgresRepoTest {

    @Test
    fun `kan opprette uprosessert klageinstanshendelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageinstanshendelsePostgresRepo = testDataHelper.klageinstanshendelsePostgresRepo
            val uprosessertKlageinstanshendelse = UprosessertKlageinstanshendelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                metadata = UprosessertKlageinstanshendelse.Metadata(
                    topic = "klage.vedtak-fattet.v1",
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 1,
                    partisjon = 2,
                    key = UUID.randomUUID().toString(),
                    value = "{}",
                ),
            ).also {
                klageinstanshendelsePostgresRepo.lagre(it)
            }
            klageinstanshendelsePostgresRepo.hentUbehandlaKlageinstanshendelser() shouldBe listOf(uprosessertKlageinstanshendelse)
        }
    }

    @Test
    fun `Dedup på metadata's hendelseId`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageinstanshendelsePostgresRepo = testDataHelper.klageinstanshendelsePostgresRepo
            val uprosessertKlageinstanshendelse = UprosessertKlageinstanshendelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                metadata = UprosessertKlageinstanshendelse.Metadata(
                    topic = "klage.behandling-events.v1",
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 1,
                    partisjon = 2,
                    key = UUID.randomUUID().toString(),
                    value = "{}",
                ),
            ).also {
                klageinstanshendelsePostgresRepo.lagre(it)
                klageinstanshendelsePostgresRepo.lagre(it.copy(id = UUID.randomUUID()))
            }
            klageinstanshendelsePostgresRepo.hentUbehandlaKlageinstanshendelser() shouldBe listOf(uprosessertKlageinstanshendelse)
        }
    }

    @Test
    fun `Konflikt på duplikat id`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageinstanshendelsePostgresRepo = testDataHelper.klageinstanshendelsePostgresRepo
            UprosessertKlageinstanshendelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                metadata = UprosessertKlageinstanshendelse.Metadata(
                    topic = "klage.behandling-events.v1",
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 1,
                    partisjon = 2,
                    key = UUID.randomUUID().toString(),
                    value = "{}",
                ),
            ).also {
                klageinstanshendelsePostgresRepo.lagre(it)
                shouldThrow<PSQLException> {
                    klageinstanshendelsePostgresRepo.lagre(it)
                }.message shouldContain "duplicate key value violates unique constraint \"klagevedtak_pkey\""
            }
        }
    }

    @Test
    fun `Endrer og lagrer type til PROSESSERT`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val klageinstanshendelsePostgresRepo = testDataHelper.klageinstanshendelsePostgresRepo
            val id = UUID.randomUUID()
            val klage = testDataHelper.persisterKlageOversendt()

            UprosessertKlageinstanshendelse(
                id = id,
                opprettet = fixedTidspunkt,
                metadata = UprosessertKlageinstanshendelse.Metadata(
                    topic = "klage.behandling-events.v1",
                    hendelseId = UUID.randomUUID().toString(),
                    offset = 1,
                    partisjon = 2,
                    key = UUID.randomUUID().toString(),
                    value = "{\"kildeReferanse\": ${klage.id}}",
                ),
            ).also {
                klageinstanshendelsePostgresRepo.lagre(it)
                klageinstanshendelsePostgresRepo.lagre(
                    ProsessertKlageinstanshendelse(
                        id = it.id,
                        opprettet = fixedTidspunkt,
                        klageId = klage.id,
                        utfall = KlageinstansUtfall.STADFESTELSE,
                        journalpostIDer = listOf(JournalpostId(UUID.randomUUID().toString())),
                        oppgaveId = null,
                    ),
                )
                klageinstanshendelsePostgresRepo.hentUbehandlaKlageinstanshendelser() shouldBe emptyList()
            }
        }
    }
}
