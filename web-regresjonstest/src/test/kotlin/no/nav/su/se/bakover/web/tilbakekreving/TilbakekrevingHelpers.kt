package no.nav.su.se.bakover.web.tilbakekreving

import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsekonsumenterPostgresRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import java.util.UUID

internal fun AppComponents.opprettOppgave(saksversjon: Long): Long {
    this.tilbakekrevingskomponenter.services.opprettOppgaveForTilbakekrevingshendelserKonsument.opprettOppgaver(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

internal fun AppComponents.oppdaterOppgave(saksversjon: Long): Long {
    this.tilbakekrevingskomponenter.services.oppdaterOppgaveForTilbakekrevingshendelserKonsument.oppdaterOppgaver(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

internal fun AppComponents.lukkOppgave(saksversjon: Long): Long {
    this.tilbakekrevingskomponenter.services.lukkOppgaveForTilbakekrevingshendelserKonsument.lukkOppgaver(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

internal fun AppComponents.genererDokumenterForForhåndsvarsel(saksversjon: Long): Long {
    this.tilbakekrevingskomponenter.services.genererDokumentForForhåndsvarselTilbakekrevingKonsument.genererDokumenter(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

internal fun AppComponents.journalførDokmenter(saksversjon: Long): Long {
    this.dokumentHendelseKomponenter.services.journalførtDokumentHendelserKonsument.journalførDokumenter(
        correlationId = CorrelationId.generate(),
    )
    return saksversjon + 1
}

internal fun AppComponents.verifiserJournalførDokumenterKonsument(antallJournalførteDokumenter: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'JournalførDokumentHendelserKonsument'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallJournalførteDokumenter
        }
    }
}

internal fun AppComponents.verifiserDokumentHendelser(
    sakId: String,
    antallGenererteDokumenter: Int,
    antallJournalførteDokumenter: Int,
) {
    val dokumenter = this.databaseRepos.dokumentHendelseRepo.hentForSak(UUID.fromString(sakId))
    dokumenter.size shouldBe antallGenererteDokumenter + antallJournalførteDokumenter

    dokumenter.filterIsInstance<GenerertDokumentHendelse>().size shouldBe antallGenererteDokumenter
    dokumenter.filterIsInstance<JournalførtDokumentHendelse>().size shouldBe antallJournalførteDokumenter
}

internal fun AppComponents.verifiserOppgaveHendelser(
    sakId: String,
    antallOppdaterteOppgaver: Int,
    antallLukketOppgaver: Int,
) {
    val oppgaveHendelser = this.databaseRepos.oppgaveHendelseRepo.hentForSak(UUID.fromString(sakId))
    // nr 1 er opprettet
    oppgaveHendelser.size shouldBe 1 + antallOppdaterteOppgaver + antallLukketOppgaver

    // en serie med tilbakekreving vil bare ha kun 1 opprettet oppgave
    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Opprettet>().single()
    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Oppdatert>().size shouldBe antallOppdaterteOppgaver
    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Lukket>().size shouldBe antallLukketOppgaver
}

internal fun AppComponents.verifiserOpprettetOppgaveKonsument() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'OpprettOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.single()
        }
    }
}

internal fun AppComponents.verifiserOppdatertOppgaveKonsument(antallOppdatertHendelser: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'OppdaterOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallOppdatertHendelser
        }
    }
}

internal fun AppComponents.verifiserLukketOppgaveKonsument() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'LukkOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.single()
        }
    }
}

internal fun AppComponents.verifiserGenererDokumentForForhåndsvarselKonsument() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'GenererDokumentForForhåndsvarselTilbakekrevingKonsument'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.single()
        }
    }
}

internal fun verifiserKravgrunnlagPåSak(
    sakId: String,
    client: HttpClient,
    forventerKravgrunnlag: Boolean,
    versjon: Int,
) {
    hentSak(sakId, client = client).also { sakJson ->
        // Kravgrunnlaget vil være utestående så lenge vi ikke har iverksatt tilbakekrevingsbehandlingen.
        JSONObject(sakJson).isNull("uteståendeKravgrunnlag") shouldBe !forventerKravgrunnlag
        JSONObject(sakJson).getInt("versjon") shouldBe versjon
    }
}
