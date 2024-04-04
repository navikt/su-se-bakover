package tilbakekreving.domain

import dokument.domain.DokumentHendelser
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlagPåSakHendelse
import no.nav.su.se.bakover.test.nyForhåndsvarsletTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyIverksattTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyOpprettetTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.nyTilbakekrevingsbehandlingTilAttesteringHendelse
import no.nav.su.se.bakover.test.nyVurdertTilbakekrevingsbehandlingHendelse
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelser

class TilbakekrevingsbehandlingHendelserTest {

    @Test
    fun `Nytt kravgrunnlag etter tilbakekrevingsbehandling - tilbakekrevingsbehandlingen referer til første kravgrunnlag`() {
        val behandlingId = TilbakekrevingsbehandlingId.fraString("8da32b16-596c-4970-8412-986219740021")
        val kravgrunnlagPåSakHendelse = HendelseId.fromString("017e1991-97fa-40fd-8840-58278a33eff4")

        val førsteKravgrunnlagSomTilbakekrevingBehandler = kravgrunnlagPåSakHendelse(
            hendelseId = kravgrunnlagPåSakHendelse,
            tidligereHendelseId = HendelseId.fromString("417c185f-a7c4-49d1-af3f-9e1d96553f5e"),
            sakId = sakId,
            eksternKravgrunnlagId = "450434",
            eksternVedtakId = "679611",
            saksnummer = saksnummer,
            kravgrunnlag = kravgrunnlag(
                saksnummer = saksnummer,
                kravgrunnlagId = "450434",
                vedtakId = "679611",
                status = Kravgrunnlagstatus.Nytt,
                kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse,
            ),
        )
        val opprettet = nyOpprettetTilbakekrevingsbehandlingHendelse(
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse,
            behandlingId = behandlingId,
        )
        val forhåndsvarslet = nyForhåndsvarsletTilbakekrevingsbehandlingHendelse(
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse,
            behandlingId = behandlingId,
            forrigeHendelse = opprettet,
        )
        val vurdert = nyVurdertTilbakekrevingsbehandlingHendelse(
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse,
            behandlingId = behandlingId,
            forrigeHendelse = forhåndsvarslet,
        )
        val vedtaksbrev = nyOppdaterVedtaksbrevTilbakekrevingsbehandlingHendelse(
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse,
            behandlingId = behandlingId,
            forrigeHendelse = vurdert,
        )
        val attestering = nyTilbakekrevingsbehandlingTilAttesteringHendelse(
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse,
            behandlingId = behandlingId,
            forrigeHendelse = vedtaksbrev,
        )
        val iverksatt = nyIverksattTilbakekrevingsbehandlingHendelse(
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelse,
            behandlingId = behandlingId,
            forrigeHendelse = attestering,
        )

        val behandlingserie = listOf(
            opprettet,
            forhåndsvarslet,
            vurdert,
            vedtaksbrev,
            attestering,
            iverksatt,
        )

        val kravgrunnlagPåSakhendelse2 = HendelseId.generer()
        val nyttKravgrunnlagEtterBehandling = kravgrunnlagPåSakHendelse(
            hendelseId = kravgrunnlagPåSakhendelse2,
            versjon = Hendelsesversjon(22),
            sakId = sakId,
            tidligereHendelseId = HendelseId.fromString("4ac79996-7c30-4720-921f-19e19cce76bb"),
            eksternKravgrunnlagId = "452015",
            eksternVedtakId = "679804",
            saksnummer = saksnummer,
            kravgrunnlag = kravgrunnlag(
                saksnummer = saksnummer,
                kravgrunnlagId = "452015",
                vedtakId = "679804",
                eksternTidspunkt = Tidspunkt.parse("2024-03-19T18:51:46.653192Z"),
                status = Kravgrunnlagstatus.Nytt,
                kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakhendelse2,
            ),
        )

        assertDoesNotThrow {
            val kek = TilbakekrevingsbehandlingHendelser.create(
                sakId = sakId,
                clock = fixedClock,
                fnr = fnr,
                saksnummer = saksnummer,
                hendelser = behandlingserie,
                kravgrunnlagPåSak = KravgrunnlagPåSakHendelser(
                    hendelser = listOf(førsteKravgrunnlagSomTilbakekrevingBehandler, nyttKravgrunnlagEtterBehandling),
                ),
                dokumentHendelser = DokumentHendelser.empty(sakId),
            ).currentState

            kek.behandlinger.size shouldBe 1
            kek.behandlinger.first().kravgrunnlag shouldBe førsteKravgrunnlagSomTilbakekrevingBehandler.kravgrunnlag
        }
    }
}
