package no.nav.su.se.bakover.database.notat

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.notat.Notat
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatHendelse
import no.nav.su.se.bakover.domain.notat.NotatVedlegg
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class VedleggRepoTest(private val dataSource: DataSource) {

    private val clock = Clock.systemUTC()

    private fun TestDataHelper.lagNotat(sakId: UUID): Notat {
        val nå = Tidspunkt.now(clock)
        val notat = Notat(
            id = UUID.randomUUID(),
            sakId = sakId,
            referanseId = UUID.randomUUID(),
            notat = "Testnotat",
            opprettet = nå,
            endret = nå,
            hendelser = listOf(
                NotatHendelse(
                    navIdent = NavIdentBruker.Saksbehandler("Z123456"),
                    tidspunkt = nå,
                    handling = NotatHandling.OPPRETTET,
                ),
            ),
        )
        notatRepo.opprett(notat)
        return notat
    }

    @Test
    fun `leggTil og hent vedlegg`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.vedleggRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = testDataHelper.lagNotat(sak.id)
        val innhold = "PDF-innhold".toByteArray()

        val vedlegg = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notat.id,
            filnavn = "test.pdf",
            mimeType = "application/pdf",
            innhold = innhold,
            opprettet = Tidspunkt.now(clock),
        )

        repo.leggTil(vedlegg)
        val hentet = repo.hent(vedlegg.id)
        hentet shouldBe vedlegg
    }

    @Test
    fun `hentForNotat returnerer alle vedlegg for notat`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.vedleggRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = testDataHelper.lagNotat(sak.id)
        val nå = Tidspunkt.now(clock)

        val vedlegg1 = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notat.id,
            filnavn = "fil1.pdf",
            mimeType = "application/pdf",
            innhold = "innhold1".toByteArray(),
            opprettet = nå,
        )
        val vedlegg2 = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notat.id,
            filnavn = "fil2.jpg",
            mimeType = "image/jpeg",
            innhold = "innhold2".toByteArray(),
            opprettet = nå,
        )

        repo.leggTil(vedlegg1)
        repo.leggTil(vedlegg2)

        val hentet = repo.hentForNotat(notat.id)
        hentet shouldContainExactlyInAnyOrder listOf(vedlegg1, vedlegg2)
    }

    @Test
    fun `hentForNotat returnerer tom liste for notat uten vedlegg`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.vedleggRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = testDataHelper.lagNotat(sak.id)

        repo.hentForNotat(notat.id) shouldBe emptyList()
    }

    @Test
    fun `slett vedlegg`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.vedleggRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = testDataHelper.lagNotat(sak.id)

        val vedlegg = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notat.id,
            filnavn = "slettes.pdf",
            mimeType = "application/pdf",
            innhold = "innhold".toByteArray(),
            opprettet = Tidspunkt.now(clock),
        )
        repo.leggTil(vedlegg)
        repo.hent(vedlegg.id) shouldBe vedlegg

        repo.slett(vedlegg.id)
        repo.hent(vedlegg.id) shouldBe null
    }

    @Test
    fun `slett vedlegg fjerner kun riktig vedlegg`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.vedleggRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = testDataHelper.lagNotat(sak.id)
        val nå = Tidspunkt.now(clock)

        val vedlegg1 = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notat.id,
            filnavn = "beholdes.pdf",
            mimeType = "application/pdf",
            innhold = "innhold1".toByteArray(),
            opprettet = nå,
        )
        val vedlegg2 = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notat.id,
            filnavn = "slettes.pdf",
            mimeType = "application/pdf",
            innhold = "innhold2".toByteArray(),
            opprettet = nå,
        )
        repo.leggTil(vedlegg1)
        repo.leggTil(vedlegg2)

        repo.slett(vedlegg2.id)

        repo.hent(vedlegg1.id) shouldBe vedlegg1
        repo.hent(vedlegg2.id) shouldBe null
        repo.hentForNotat(notat.id) shouldBe listOf(vedlegg1)
    }

    @Test
    fun `bytea innhold lagres og leses korrekt`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.vedleggRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = testDataHelper.lagNotat(sak.id)

        val originalInnhold = (0..255).map { it.toByte() }.toByteArray()
        val vedlegg = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notat.id,
            filnavn = "binær.pdf",
            mimeType = "application/pdf",
            innhold = originalInnhold,
            opprettet = Tidspunkt.now(clock),
        )
        repo.leggTil(vedlegg)

        val hentet = repo.hent(vedlegg.id)!!
        hentet.innhold.contentEquals(originalInnhold) shouldBe true
    }

    @Test
    fun `mime type lagres og leses korrekt`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.vedleggRepo
        val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
        val notat = testDataHelper.lagNotat(sak.id)

        val vedlegg = NotatVedlegg(
            id = UUID.randomUUID(),
            notatId = notat.id,
            filnavn = "bilde.png",
            mimeType = "image/png",
            innhold = "png".toByteArray(),
            opprettet = Tidspunkt.now(clock),
        )

        repo.leggTil(vedlegg)

        repo.hent(vedlegg.id)!!.mimeType shouldBe "image/png"
    }
}
