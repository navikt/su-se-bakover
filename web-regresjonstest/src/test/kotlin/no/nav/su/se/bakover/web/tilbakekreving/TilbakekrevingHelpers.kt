package no.nav.su.se.bakover.web.tilbakekreving

import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.dokument.application.consumer.DistribuerDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsekonsumenterPostgresRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.application.service.consumer.GenererDokumentForForhåndsvarselTilbakekrevingKonsument
import tilbakekreving.application.service.consumer.LukkOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OppdaterOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OpprettOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.domain.ForhåndsvarsleTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import java.util.UUID

internal fun AppComponents.hentUtførteSideeffekter(sakId: String): UtførteSideeffekter {
    val dokumentHendelser = this.databaseRepos.dokumentHendelseRepo.hentForSak(UUID.fromString(sakId)).flatMap {
        it.dokumenter
    }
    val oppgaveHendelser = this.databaseRepos.oppgaveHendelseRepo.hentForSak(UUID.fromString(sakId))
    return UtførteSideeffekter(
        antallOpprettetOppgaver = oppgaveHendelser.filterIsInstance<OppgaveHendelse.Opprettet>().size,
        antallOppdatertOppgaveHendelser = oppgaveHendelser.filterIsInstance<OppgaveHendelse.Oppdatert>().size,
        antallLukketOppgaver = oppgaveHendelser.filterIsInstance<OppgaveHendelse.Lukket>().size,
        // TODO jah: Denne/disse er generell og vil kræsje med vedtaksbrevet.
        antallGenererteForhåndsvarsler = dokumentHendelser.filterIsInstance<GenerertDokumentHendelse>().map {
            this.tilbakekrevingskomponenter.repos.tilbakekrevingsbehandlingRepo.hentHendelse(it.relatertHendelse)
        }.filterIsInstance<ForhåndsvarsleTilbakekrevingsbehandlingHendelse>().size,
        antallGenererteVedtaksbrev = dokumentHendelser.filterIsInstance<GenerertDokumentHendelse>().map {
            this.tilbakekrevingskomponenter.repos.tilbakekrevingsbehandlingRepo.hentHendelse(it.relatertHendelse)
        }.filterIsInstance<IverksattHendelse>().size,
        antallJournalførteDokumenter = dokumentHendelser.filterIsInstance<JournalførtDokumentHendelse>().size,
        antallDistribuertDokumenter = dokumentHendelser.filterIsInstance<DistribuertDokumentHendelse>().size,
    )
}

internal data class UtførteSideeffekter(
    val antallOpprettetOppgaver: Int,
    val antallOppdatertOppgaveHendelser: Int,
    val antallLukketOppgaver: Int,
    val antallGenererteForhåndsvarsler: Int,
    val antallGenererteVedtaksbrev: Int,
    val antallJournalførteDokumenter: Int,
    val antallDistribuertDokumenter: Int,
)

internal fun AppComponents.kjørAlleTilbakekrevingskonsumenter() {
    // --- oppgaver ---
    this.kjørOpprettOppgaveKonsument()
    this.oppdaterOppgave()
    this.lukkOppgave()

    // --- dokumenter ---
    this.genererDokumenterForForhåndsvarsel()
    this.genererDokumenterForVedtaksbrev()
    this.journalførDokumenter()
    this.distribuerDokumenter()
}

/**
 * Siden man sender inn [tidligereUtførteSideeffekter], oppgir man kun de nye sideeffektene som har skjedd.
 */
internal fun AppComponents.kjørAlleVerifiseringer(
    sakId: String,
    tidligereUtførteSideeffekter: UtførteSideeffekter,
    antallOpprettetOppgaver: Int = 0,
    antallOppdatertOppgaveHendelser: Int = 0,
    antallLukketOppgaver: Int = 0,
    antallGenererteForhåndsvarsler: Int = 0,
    antallGenererteVedtaksbrev: Int = 0,
    antallJournalførteDokumenter: Int = 0,
    antallDistribuertDokumenter: Int = 0,
) {
    this.verifiserOpprettetOppgaveKonsument(antallOpprettetOppgaver + tidligereUtførteSideeffekter.antallOpprettetOppgaver)
    this.verifiserOppdatertOppgaveKonsument(antallOppdatertOppgaveHendelser + tidligereUtførteSideeffekter.antallOppdatertOppgaveHendelser)
    this.verifiserLukketOppgaveKonsument(antallLukketOppgaver + tidligereUtførteSideeffekter.antallLukketOppgaver)
    this.verifiserOppgaveHendelser(
        sakId = sakId,
        antallOpprettetOppgaver = antallOpprettetOppgaver + tidligereUtførteSideeffekter.antallOpprettetOppgaver,
        antallOppdaterteOppgaver = antallOppdatertOppgaveHendelser + tidligereUtførteSideeffekter.antallOppdatertOppgaveHendelser,
        antallLukketOppgaver = antallLukketOppgaver + tidligereUtførteSideeffekter.antallLukketOppgaver,
    )

    this.verifiserGenererDokumentForForhåndsvarselKonsument(antallGenererteForhåndsvarsler + tidligereUtførteSideeffekter.antallGenererteForhåndsvarsler)
    this.verifiserGenererDokumentForVedtaksbrevKonsument(antallGenererteVedtaksbrev + tidligereUtførteSideeffekter.antallGenererteVedtaksbrev)
    this.verifiserJournalførDokumenterKonsument(antallJournalførteDokumenter + tidligereUtførteSideeffekter.antallJournalførteDokumenter)
    this.verifiserDistribuerteDokumenterKonsument(antallDistribuertDokumenter + tidligereUtførteSideeffekter.antallDistribuertDokumenter)
    this.verifiserDokumentHendelser(
        sakId = sakId,
        antallGenererteDokumenter = antallGenererteForhåndsvarsler + antallGenererteVedtaksbrev + tidligereUtførteSideeffekter.antallGenererteForhåndsvarsler + tidligereUtførteSideeffekter.antallGenererteVedtaksbrev,
        antallJournalførteDokumenter = antallJournalførteDokumenter + tidligereUtførteSideeffekter.antallJournalførteDokumenter,
        antallDistribuerteDokumenter = antallDistribuertDokumenter + tidligereUtførteSideeffekter.antallDistribuertDokumenter,
    )
}

/**
 * Kjører [OpprettOppgaveForTilbakekrevingshendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.kjørOpprettOppgaveKonsument() {
    this.tilbakekrevingskomponenter.services.opprettOppgaveForTilbakekrevingshendelserKonsument.opprettOppgaver(
        correlationId = CorrelationId.generate(),
    )
}

/**
 * Kjører [OppdaterOppgaveForTilbakekrevingshendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.oppdaterOppgave() {
    this.tilbakekrevingskomponenter.services.oppdaterOppgaveForTilbakekrevingshendelserKonsument.oppdaterOppgaver(
        correlationId = CorrelationId.generate(),
    )
}

/**
 * Kjører [LukkOppgaveForTilbakekrevingshendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk lukker en oppgave eller ikke.
 */
internal fun AppComponents.lukkOppgave() {
    this.tilbakekrevingskomponenter.services.lukkOppgaveForTilbakekrevingshendelserKonsument.lukkOppgaver(
        correlationId = CorrelationId.generate(),
    )
}

/**
 * Kjører [GenererDokumentForForhåndsvarselTilbakekrevingKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.genererDokumenterForForhåndsvarsel() {
    this.tilbakekrevingskomponenter.services.genererDokumentForForhåndsvarselTilbakekrevingKonsument.genererDokumenter(
        correlationId = CorrelationId.generate(),
    )
}

internal fun AppComponents.genererDokumenterForVedtaksbrev() {
    this.tilbakekrevingskomponenter.services.vedtaksbrevTilbakekrevingKonsument.genererVedtaksbrev(
        correlationId = CorrelationId.generate(),
    )
}

/**
 * Kjører [JournalførDokumentHendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.journalførDokumenter() {
    this.dokumentHendelseKomponenter.services.journalførtDokumentHendelserKonsument.journalførDokumenter(
        correlationId = CorrelationId.generate(),
    )
}

/**
 * Kjører [DistribuerDokumentHendelserKonsument].
 *
 * @return Denne funksjonen bumper saksversjon med 1 uavhengig om vi faktisk oppretter en oppgave eller ikke.
 */
internal fun AppComponents.distribuerDokumenter() {
    this.dokumentHendelseKomponenter.services.distribuerDokumentHendelserKonsument.distribuer(
        correlationId = CorrelationId.generate(),
    )
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

internal fun AppComponents.verifiserDistribuerteDokumenterKonsument(antallDistribuerteDokumenter: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'DistribuerDokumentHendelserKonsument'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallDistribuerteDokumenter
        }
    }
}

internal fun AppComponents.verifiserDokumentHendelser(
    sakId: String,
    antallGenererteDokumenter: Int,
    antallJournalførteDokumenter: Int,
    antallDistribuerteDokumenter: Int,
) {
    val dokumentHendelser = this.databaseRepos.dokumentHendelseRepo.hentForSak(UUID.fromString(sakId)).let {
        it.flatMap { it.dokumenter }
    }
    dokumentHendelser.size shouldBe antallGenererteDokumenter + antallJournalførteDokumenter + antallDistribuerteDokumenter

    dokumentHendelser.filterIsInstance<GenerertDokumentHendelse>().size shouldBe antallGenererteDokumenter
    dokumentHendelser.filterIsInstance<JournalførtDokumentHendelse>().size shouldBe antallJournalførteDokumenter
    dokumentHendelser.filterIsInstance<DistribuertDokumentHendelse>().size shouldBe antallDistribuerteDokumenter
}

internal fun AppComponents.verifiserOppgaveHendelser(
    sakId: String,
    antallOpprettetOppgaver: Int,
    antallOppdaterteOppgaver: Int,
    antallLukketOppgaver: Int,
) {
    val oppgaveHendelser = this.databaseRepos.oppgaveHendelseRepo.hentForSak(UUID.fromString(sakId))

    oppgaveHendelser.size shouldBe antallOpprettetOppgaver + antallOppdaterteOppgaver + antallLukketOppgaver

    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Opprettet>().size shouldBe antallOpprettetOppgaver
    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Oppdatert>().size shouldBe antallOppdaterteOppgaver
    oppgaveHendelser.filterIsInstance<OppgaveHendelse.Lukket>().size shouldBe antallLukketOppgaver
}

private fun AppComponents.verifiserOpprettetOppgaveKonsument(antallOpprettetOppgaver: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'OpprettOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size.also {
                withClue("Forventet $antallOpprettetOppgaver rader med konsumentId OpprettOppgaveForTilbakekrevingsbehandlingHendelser, men var $it") {
                    it shouldBe antallOpprettetOppgaver
                }
            }
            """
                select * from hendelse where type = 'OPPRETTET_TILBAKEKREVINGSBEHANDLING'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size.let {
                withClue("Forventet $antallOpprettetOppgaver hendelser med type OPPRETTET_TILBAKEKREVINGSBEHANDLING, men var $it") {
                    it shouldBe antallOpprettetOppgaver
                }
            }
        }
    }
}

/**
 * Sletter ikke selve hendelsen, men kun verifikasjonen på at vi har kjørt konsumenten.
 * Dette for å verifisere at vi ikke oppretter oppgaver på nytt selvom dette skjer.
 * Siden vi skal ha en dedup. på hendelsene så vi oppretter oppgaver på nytt.
 */
internal fun AppComponents.slettOpprettetOppgaveKonsumentJobb() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                delete from hendelse_konsument where konsumentId = 'OpprettOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().oppdatering(emptyMap(), it)
        }
    }
}

private fun AppComponents.verifiserOppdatertOppgaveKonsument(antallOppdatertHendelser: Int) {
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

/**
 * Sletter ikke selve hendelsen, men kun verifikasjonen på at vi har kjørt konsumenten.
 * Dette for å verifisere at vi ikke oppretter oppgaver på nytt selvom dette skjer.
 * Siden vi skal ha en dedup. på hendelsene så vi oppretter oppgaver på nytt.
 */
internal fun AppComponents.slettOppdatertOppgaveKonsumentJobb() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                delete from hendelse_konsument where konsumentId = 'OppdaterOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().oppdatering(emptyMap(), it)
        }
    }
}

private fun AppComponents.verifiserLukketOppgaveKonsument(antallLukketOppgaver: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'LukkOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallLukketOppgaver
        }
    }
}

/**
 * Sletter ikke selve hendelsen, men kun verifikasjonen på at vi har kjørt konsumenten.
 * Dette for å verifisere at vi ikke oppretter oppgaver på nytt selvom dette skjer.
 * Siden vi skal ha en dedup. på hendelsene så vi oppretter oppgaver på nytt.
 */
internal fun AppComponents.slettLukketOppgaveKonsumentJobb() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                delete from hendelse_konsument where konsumentId = 'LukkOppgaveForTilbakekrevingsbehandlingHendelser'
            """.trimIndent().oppdatering(emptyMap(), it)
        }
    }
}

private fun AppComponents.verifiserGenererDokumentForForhåndsvarselKonsument(antallGenerert: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'GenererDokumentForForhåndsvarselTilbakekrevingKonsument'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallGenerert
        }
    }
}

private fun AppComponents.verifiserGenererDokumentForVedtaksbrevKonsument(antallGenerert: Int) {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                select * from hendelse_konsument where konsumentId = 'GenererVedtaksbrevTilbakekrevingKonsument'
            """.trimIndent().hentListe(emptyMap(), it) {
                it.string("hendelseId")
            }.size shouldBe antallGenerert
        }
    }
}

/**
 * Sletter ikke selve hendelsen, men kun verifikasjonen på at vi har kjørt konsumenten.
 * Dette for å verifisere at vi ikke oppretter oppgaver på nytt selvom dette skjer.
 * Siden vi skal ha en dedup. på hendelsene så vi oppretter oppgaver på nytt.
 */
internal fun AppComponents.slettGenererDokumentForForhåndsvarselKonsumentJobb() {
    this.databaseRepos.hendelsekonsumenterRepo.let {
        (it as HendelsekonsumenterPostgresRepo).sessionFactory.withSession {
            """
                delete from hendelse_konsument where konsumentId = 'GenererDokumentForForhåndsvarselTilbakekrevingKonsument'
            """.trimIndent().oppdatering(emptyMap(), it)
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
