package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class JournalføringOgBrevdistribusjonTest {
    @Nested
    inner class EtterUtbetaling {
        @Test
        fun `manglende journalpostid og brevbestillingsid ger status venterPåKvittering`() {
            JournalføringOgBrevdistribusjon.fromId(
                iverksattJournalpostId = null,
                iverksattBrevbestillingId = null,
            ) shouldBe JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert.also {
                JournalføringOgBrevdistribusjon.iverksattJournalpostId(it) shouldBe null
                JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `kun journalpostid ger status journalført`() {
            JournalføringOgBrevdistribusjon.fromId(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = null,
            ) shouldBe JournalføringOgBrevdistribusjon.Journalført(JournalpostId("13")).also {
                JournalføringOgBrevdistribusjon.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `både journalpostid og brevbestillingsid ger status JournalførtOgDistribuertBrev`() {
            JournalføringOgBrevdistribusjon.fromId(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = BrevbestillingId("45"),
            ) shouldBe JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                JournalpostId("13"),
                BrevbestillingId("45"),
            ).also {
                JournalføringOgBrevdistribusjon.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(it) shouldBe BrevbestillingId("45")
            }
        }

        @Test
        fun `brevbestillingsid uten journalpostid skal kaste exception`() {
            assertThrows<IllegalStateException> {
                JournalføringOgBrevdistribusjon.fromId(
                    iverksattJournalpostId = null,
                    iverksattBrevbestillingId = BrevbestillingId("45"),
                )
            }
        }
    }

    @Nested
    inner class IngenEndring {
        @Test
        fun `kun journalpostid ger status journalført`() {
            JournalføringOgBrevdistribusjon.fromId(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = null,
            ) shouldBe JournalføringOgBrevdistribusjon.Journalført(JournalpostId("13")).also {
                JournalføringOgBrevdistribusjon.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `både journalpostid og brevbestillingsid ger status JournalførtOgDistribuertBrev`() {
            JournalføringOgBrevdistribusjon.fromId(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = BrevbestillingId("45"),
            ) shouldBe JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(
                JournalpostId("13"),
                BrevbestillingId("45"),
            ).also {
                JournalføringOgBrevdistribusjon.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(it) shouldBe BrevbestillingId("45")
            }
        }

        @Test
        fun `brevbestillingsid uten journalpostid skal kaste exception`() {
            assertThrows<IllegalStateException> {
                JournalføringOgBrevdistribusjon.fromId(
                    iverksattJournalpostId = null,
                    iverksattBrevbestillingId = BrevbestillingId("45"),
                )
            }
        }
    }
}
