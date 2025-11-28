package no.nav.su.se.bakover.database.klage

import behandling.klage.domain.UprosessertKlageinstanshendelse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.oppgave.oppgaveId
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.postgresql.util.PSQLException
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class KlageinstanshendelsePostgresRepoTest(private val dataSource: DataSource) {

    @Test
    fun `kan opprette uprosessert klageinstanshendelse`() {
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

    @Test
    fun `Dedup på metadata's hendelseId`() {
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

    @Test
    fun `Konflikt på duplikat id`() {
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

    @Test
    fun `Endrer og lagrer type til PROSESSERT`() {
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
                ProsessertKlageinstanshendelse.KlagebehandlingAvsluttet(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    klageId = klage.id,
                    utfall = AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse,
                    journalpostIDer = listOf(JournalpostId(UUID.randomUUID().toString())),
                    oppgaveId = oppgaveId,
                ),
            )
            klageinstanshendelsePostgresRepo.hentUbehandlaKlageinstanshendelser() shouldBe emptyList()
        }
    }
}
