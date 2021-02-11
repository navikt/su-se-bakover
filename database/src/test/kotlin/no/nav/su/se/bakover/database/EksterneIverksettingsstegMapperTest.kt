package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegForAvslag
import no.nav.su.se.bakover.domain.journal.JournalpostId
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class EksterneIverksettingsstegMapperTest {
    @Nested
    inner class EtterUtbetaling {
        @Test
        fun `manglende journalpostid og brevbestillingsid ger status venterPåKvittering`() {
            EksterneIverksettingsstegEtterUtbetalingMapper.idToObject(
                iverksattJournalpostId = null,
                iverksattBrevbestillingId = null
            ) shouldBe EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering.also {
                EksterneIverksettingsstegEtterUtbetalingMapper.iverksattJournalpostId(it) shouldBe null
                EksterneIverksettingsstegEtterUtbetalingMapper.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `kun journalpostid ger status journalført`() {
            EksterneIverksettingsstegEtterUtbetalingMapper.idToObject(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = null
            ) shouldBe EksterneIverksettingsstegEtterUtbetaling.Journalført(JournalpostId("13")).also {
                EksterneIverksettingsstegEtterUtbetalingMapper.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                EksterneIverksettingsstegEtterUtbetalingMapper.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `både journalpostid og brevbestillingsid ger status JournalførtOgDistribuertBrev`() {
            EksterneIverksettingsstegEtterUtbetalingMapper.idToObject(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = BrevbestillingId("45")
            ) shouldBe EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev(
                JournalpostId("13"),
                BrevbestillingId("45")
            ).also {
                EksterneIverksettingsstegEtterUtbetalingMapper.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                EksterneIverksettingsstegEtterUtbetalingMapper.iverksattBrevbestillingId(it) shouldBe BrevbestillingId("45")
            }
        }

        @Test
        fun `brevbestillingsid uten journalpostid skal kaste exception`() {
            assertThrows<IllegalStateException> {
                EksterneIverksettingsstegEtterUtbetalingMapper.idToObject(
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
            EksterneIverksettingsstegForAvslagMapper.idToObject(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = null
            ) shouldBe EksterneIverksettingsstegForAvslag.Journalført(JournalpostId("13")).also {
                EksterneIverksettingsstegForAvslagMapper.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                EksterneIverksettingsstegForAvslagMapper.iverksattBrevbestillingId(it) shouldBe null
            }
        }

        @Test
        fun `både journalpostid og brevbestillingsid ger status JournalførtOgDistribuertBrev`() {
            EksterneIverksettingsstegForAvslagMapper.idToObject(
                iverksattJournalpostId = JournalpostId("13"),
                iverksattBrevbestillingId = BrevbestillingId("45")
            ) shouldBe EksterneIverksettingsstegForAvslag.JournalførtOgDistribuertBrev(
                JournalpostId("13"),
                BrevbestillingId("45")
            ).also {
                EksterneIverksettingsstegForAvslagMapper.iverksattJournalpostId(it) shouldBe JournalpostId("13")
                EksterneIverksettingsstegForAvslagMapper.iverksattBrevbestillingId(it) shouldBe BrevbestillingId("45")
            }
        }

        @Test
        fun `brevbestillingsid uten journalpostid skal kaste exception`() {
            assertThrows<IllegalStateException> {
                EksterneIverksettingsstegForAvslagMapper.idToObject(
                    iverksattJournalpostId = null,
                    iverksattBrevbestillingId = BrevbestillingId("45")
                )
            }
        }
    }
}
