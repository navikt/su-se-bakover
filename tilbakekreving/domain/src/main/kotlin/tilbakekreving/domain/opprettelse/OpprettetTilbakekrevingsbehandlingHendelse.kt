@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som TilbakekrevingsbehandlingHendelse (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagDetaljerPåSakHendelse
import java.time.Clock
import java.util.UUID

/**
 * [tilbakekreving.domain.opprettelse.OpprettTilbakekrevingsbehandlingCommand] fører potensielt til en [OpprettetTilbakekrevingsbehandlingHendelse].
 * Selve tilstanden (som knyttes til Sak.kt) representeres ved [OpprettetTilbakekrevingsbehandling]
 *
 * @param id knytter en serie med tilbakekrevingsbehandling hendelser (de som hører til samme behandling)
 */
data class OpprettetTilbakekrevingsbehandlingHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val id: TilbakekrevingsbehandlingId,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val kravgrunnlagPåSakHendelseId: HendelseId,
) : TilbakekrevingsbehandlingHendelse {

    override val utførtAv: NavIdentBruker.Saksbehandler = opprettetAv

    // Dette vil være den første hendelsen i denne behandlingen.
    override val tidligereHendelseId: HendelseId? = null

    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        /**
         * Oppretter en opprettet tilbakekrevingsbehandlinghendelse med en tilfeldig id.
         */
        fun opprett(
            sakId: UUID,
            opprettetAv: NavIdentBruker.Saksbehandler,
            versjon: Hendelsesversjon,
            clock: Clock,
            kravgrunnlagPåSakHendelseId: HendelseId,
        ) = OpprettetTilbakekrevingsbehandlingHendelse(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            opprettetAv = opprettetAv,
            versjon = versjon,
            id = TilbakekrevingsbehandlingId.generer(),
            kravgrunnlagPåSakHendelseId = kravgrunnlagPåSakHendelseId,
        )
    }

    fun toDomain(
        fnr: Fnr,
        kravgrunnlagPåSakHendelse: KravgrunnlagDetaljerPåSakHendelse,
        erKravgrunnlagUtdatert: Boolean,
    ): OpprettetTilbakekrevingsbehandling {
        return toDomain(fnr, kravgrunnlagPåSakHendelse.kravgrunnlag, erKravgrunnlagUtdatert)
    }

    fun toDomain(
        fnr: Fnr,
        kravgrunnlag: Kravgrunnlag,
        erKravgrunnlagUtdatert: Boolean,
    ): OpprettetTilbakekrevingsbehandling {
        require(kravgrunnlag.hendelseId == this.kravgrunnlagPåSakHendelseId)
        return OpprettetTilbakekrevingsbehandling(
            id = id,
            sakId = sakId,
            fnr = fnr,
            saksnummer = kravgrunnlag.saksnummer,
            opprettet = hendelsestidspunkt,
            opprettetAv = opprettetAv,
            kravgrunnlag = kravgrunnlag,
            versjon = versjon,
            hendelseId = hendelseId,
            erKravgrunnlagUtdatert = erKravgrunnlagUtdatert,
        )
    }
}

data class OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse(
    override val hendelseId: HendelseId,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val versjon: Hendelsesversjon,
    override val id: TilbakekrevingsbehandlingId,
    val opprettetAv: NavIdentBruker.Saksbehandler,
) : TilbakekrevingsbehandlingHendelse {
    override val utførtAv: NavIdentBruker.Saksbehandler = opprettetAv

    // Dette vil være den første hendelsen i denne behandlingen.
    override val tidligereHendelseId: HendelseId? = null
    override val entitetId: UUID = sakId

    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId && this.sakId == other.sakId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        /**
         * Oppretter en opprettet tilbakekrevingsbehandlinghendelse med en tilfeldig id.
         */
        fun opprett(
            sakId: UUID,
            opprettetAv: NavIdentBruker.Saksbehandler,
            versjon: Hendelsesversjon,
            clock: Clock,
        ) = OpprettetTilbakekrevingsbehandlingUtenKravgrunnlagHendelse(
            hendelseId = HendelseId.generer(),
            sakId = sakId,
            hendelsestidspunkt = Tidspunkt.now(clock),
            opprettetAv = opprettetAv,
            versjon = versjon,
            id = TilbakekrevingsbehandlingId.generer(),
        )
    }

    fun toDomain(
        fnr: Fnr,
        saksnummer: Saksnummer,
        erKravgrunnlagUtdatert: Boolean,
    ): OpprettetTilbakekrevingsbehandlingUtenKravgrunnlag {
        return OpprettetTilbakekrevingsbehandlingUtenKravgrunnlag(
            id = id,
            sakId = sakId,
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = hendelsestidspunkt,
            opprettetAv = opprettetAv,
            versjon = versjon,
            hendelseId = hendelseId,
            erKravgrunnlagUtdatert = erKravgrunnlagUtdatert,
        )
    }
}
