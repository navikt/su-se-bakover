package no.nav.su.se.bakover.service.statistikk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

internal class SøknadStatistikkMapperTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, zoneIdOslo)

    @Test
    fun `mapper ny søknad`() {
        val saksnummer = Saksnummer(2079L)
        val søknad = Søknad.Ny(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        )

        SøknadStatistikkMapper(fixedClock).map(søknad, saksnummer, Statistikk.Behandling.SøknadStatus.SØKNAD_MOTTATT) shouldBe Statistikk.Behandling(
            funksjonellTid = søknad.opprettet,
            tekniskTid = søknad.opprettet,
            mottattDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknad.id,
            relatertBehandlingId = null,
            sakId = søknad.sakId,
            saksnummer = saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = "Søknad for SU Uføre",
            behandlingStatus = "SØKNAD_MOTTATT",
            behandlingStatusBeskrivelse = "Søknaden er mottatt",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = false,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = null,
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = null,
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = false,
        )
    }
    @Test
    fun `mapper lukket søknad`() {
        val saksnummer = Saksnummer(2079L)
        val søknad = Søknad.Lukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId("journalpostid"),
            oppgaveId = OppgaveId("oppgaveid"),
            lukketTidspunkt = Tidspunkt.now(fixedClock),
            lukketAv = NavIdentBruker.Saksbehandler(navIdent = "Mr Lukker"),
            lukketType = Søknad.Lukket.LukketType.AVVIST,
            lukketJournalpostId = JournalpostId("journalpostid"),
            lukketBrevbestillingId = BrevbestillingId("brevbestillingid"),
        )

        SøknadStatistikkMapper(fixedClock).map(søknad, saksnummer, Statistikk.Behandling.SøknadStatus.SØKNAD_LUKKET) shouldBe Statistikk.Behandling(
            funksjonellTid = søknad.lukketTidspunkt,
            tekniskTid = søknad.lukketTidspunkt,
            mottattDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            registrertDato = søknad.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = søknad.id,
            relatertBehandlingId = null,
            sakId = søknad.sakId,
            saksnummer = saksnummer.nummer,
            behandlingType = Statistikk.Behandling.BehandlingType.SOKNAD,
            behandlingTypeBeskrivelse = "Søknad for SU Uføre",
            behandlingStatus = "SØKNAD_LUKKET",
            behandlingStatusBeskrivelse = "Søknaden er lukket",
            utenlandstilsnitt = "NASJONAL",
            utenlandstilsnittBeskrivelse = null,
            ansvarligEnhetKode = "4815",
            ansvarligEnhetType = "NORG",
            behandlendeEnhetKode = "4815",
            behandlendeEnhetType = "NORG",
            totrinnsbehandling = false,
            avsender = "su-se-bakover",
            versjon = fixedClock.millis(),
            vedtaksDato = null,
            vedtakId = null,
            resultat = "AVVIST",
            resultatBegrunnelse = null,
            resultatBegrunnelseBeskrivelse = null,
            resultatBeskrivelse = null,
            beslutter = null,
            saksbehandler = "Mr Lukker",
            behandlingOpprettetAv = null,
            behandlingOpprettetType = null,
            behandlingOpprettetTypeBeskrivelse = null,
            datoForUttak = null,
            datoForUtbetaling = null,
            avsluttet = true
        )
    }
}
