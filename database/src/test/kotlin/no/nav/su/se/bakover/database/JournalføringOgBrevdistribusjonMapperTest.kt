package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class JournalføringOgBrevdistribusjonMapperTest {
    @Nested
    inner class EtterUtbetaling {
        @Test
        fun `manglende journalpostid og brevbestillingsid ger status venterPåKvittering`() {
            JournalføringOgBrevdistribusjonMapper.idToObject(
                iverksattJournalpostId = null,
                iverksattBrevbestillingId = null
            ) shouldBe JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert.also {
                JournalføringOgBrevdistribusjonMapper.iverksattJournalpostId(it) shouldBe null
                JournalføringOgBrevdistribusjonMapper.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `kun journalpostid ger status journalført`() {
            JournalføringOgBrevdistribusjonMapper.idToObject(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = null
            ) shouldBe JournalføringOgBrevdistribusjon.Journalført(JournalpostId("13")).also {
                JournalføringOgBrevdistribusjonMapper.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                JournalføringOgBrevdistribusjonMapper.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `både journalpostid og brevbestillingsid ger status JournalførtOgDistribuertBrev`() {
            JournalføringOgBrevdistribusjonMapper.idToObject(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = BrevbestillingId("45")
            ) shouldBe JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                JournalpostId("13"),
                BrevbestillingId("45")
            ).also {
                JournalføringOgBrevdistribusjonMapper.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                JournalføringOgBrevdistribusjonMapper.iverksattBrevbestillingId(it) shouldBe BrevbestillingId("45")
            }
        }

        @Test
        fun `brevbestillingsid uten journalpostid skal kaste exception`() {
            assertThrows<IllegalStateException> {
                JournalføringOgBrevdistribusjonMapper.idToObject(
                    iverksattJournalpostId = null,
                    iverksattBrevbestillingId = BrevbestillingId("45")
                )
            }
        }
    }

    @Nested
    inner class Avslag {
        @Test
        fun `kun journalpostid ger status journalført`() {
            JournalføringOgBrevdistribusjonMapper.idToObject(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = null
            ) shouldBe JournalføringOgBrevdistribusjon.Journalført(JournalpostId("13")).also {
                JournalføringOgBrevdistribusjonMapper.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                JournalføringOgBrevdistribusjonMapper.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `både journalpostid og brevbestillingsid ger status JournalførtOgDistribuertBrev`() {
            JournalføringOgBrevdistribusjonMapper.idToObject(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = BrevbestillingId("45")
            ) shouldBe JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                JournalpostId("13"),
                BrevbestillingId("45")
            ).also {
                JournalføringOgBrevdistribusjonMapper.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                JournalføringOgBrevdistribusjonMapper.iverksattBrevbestillingId(it) shouldBe BrevbestillingId("45")
            }
        }

        @Test
        fun `brevbestillingsid uten journalpostid skal kaste exception`() {
            assertThrows<IllegalStateException> {
                JournalføringOgBrevdistribusjonMapper.idToObject(
                    iverksattJournalpostId = null,
                    iverksattBrevbestillingId = BrevbestillingId("45")
                )
            }
        }
    }
}
