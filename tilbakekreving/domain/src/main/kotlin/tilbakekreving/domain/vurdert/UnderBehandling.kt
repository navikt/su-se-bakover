@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselMetaInfo
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdert.Vurderinger
import java.lang.IllegalStateException
import java.util.UUID

/**
 * Den vanlige flyten er:
 *  1. Opprett
 *  1. Forhåndsvarsle
 *  1. Vurder
 *  1. Fatt vedtaksbrev
 *  1. Send til attestering
 *  1. Iverksett eller underkjenn
 *
 */
sealed interface UnderBehandling :
    KanOppdatereKravgrunnlag,
    KanLeggeTilBrev,
    KanVurdere,
    KanForhåndsvarsle,
    UnderBehandlingEllerTilAttestering {

    override val månedsvurderinger: Vurderinger?
    override fun erÅpen() = true

    val erUnderkjent: Boolean

    /**
     * Kan kun gå fra [OpprettetTilbakekrevingsbehandling] til [Påbegynt], men ikke tilbake til [OpprettetTilbakekrevingsbehandling].
     * Lovelige overganger til:
     *  * [AvbruttTilbakekrevingsbehandling]
     *  * [Påbegynt]
     *  * [Utfylt]
     *
     *  Forrige steg, kan bare være [OpprettetTilbakekrevingsbehandling] eller [Påbegynt]
     */
    data class Påbegynt(
        val forrigeSteg: KanEndres,
        override val hendelseId: HendelseId,
        override val versjon: Hendelsesversjon,
        override val månedsvurderinger: Vurderinger?,
        override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo>,
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = null,
        override val kravgrunnlag: Kravgrunnlag,
    ) : UnderBehandling, KanEndres by forrigeSteg {
        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()

        // Behandlingen må være utfylt før man kan attestere/underkjenne.
        override val erUnderkjent = false
        override fun erÅpen(): Boolean = true

        init {
            // TODO jah: Kan vi type oss ut av dette?
            require(forrigeSteg is OpprettetTilbakekrevingsbehandling || forrigeSteg is Påbegynt)
        }

        fun erVurdert(): Boolean = månedsvurderinger != null

        /**
         * Siden vedtaksbrevet er avhengig av månedsperiodene krever vi at månedsvurderingene er utfylt først.
         * Kan vurdere å gjøre Påbegynt til sealed og dele den opp i med og uten brev.
         */
        override fun oppdaterVedtaksbrev(
            vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ): Utfylt {
            return if (månedsvurderinger == null) {
                throw IllegalArgumentException("Må gjøre månedsvurderingene før man tar stilling til vedtaksbrev")
            } else {
                Utfylt(
                    forrigeSteg = this,
                    månedsvurderinger = månedsvurderinger,
                    hendelseId = hendelseId,
                    vedtaksbrevvalg = vedtaksbrevvalg,
                    attesteringer = forrigeSteg.attesteringer,
                    forhåndsvarselsInfo = forhåndsvarselsInfo,
                    versjon = versjon,
                )
            }
        }

        override fun leggTilForhåndsvarselDokumentId(
            dokumentId: UUID,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
            hendelsesTidspunkt: Tidspunkt,
        ): Påbegynt {
            return this.copy(
                forhåndsvarselsInfo = this.forhåndsvarselsInfo.plus(
                    ForhåndsvarselMetaInfo(dokumentId, hendelsesTidspunkt),
                ),
                hendelseId = hendelseId,
                versjon = versjon,
            )
        }

        override fun oppdaterKravgrunnlag(
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
            nyttKravgrunnlag: Kravgrunnlag,
        ): Påbegynt {
            if (this.kravgrunnlag.eksternKravgrunnlagId == nyttKravgrunnlag.eksternKravgrunnlagId) {
                throw IllegalStateException("Prøvde å oppdatere kravgrunnlag for behandling ${this.id}, men kravgrunnlags-id'en er lik")
            }
            return this.copy(
                hendelseId = hendelseId,
                versjon = versjon,
                månedsvurderinger = null,
                kravgrunnlag = nyttKravgrunnlag,
            )
        }

        override fun leggTilVurderinger(
            månedsvurderinger: Vurderinger,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = this.copy(
            månedsvurderinger = månedsvurderinger,
            hendelseId = hendelseId,
            versjon = versjon,
        )
    }

    /**
     * Når vi først er utfylt, kan vi ikke gå tilbake til påbegynt.
     * Lovelige overganger er:
     *   *  [AvbruttTilbakekrevingsbehandling]
     *   * [TilbakekrevingsbehandlingTilAttestering]
     *
     *   @param forhåndsvarselsInfo Vi støtter og legge til nye forhåndsvarslinger selvom tilstanden er [Utfylt]
     *   @property erUnderkjent Dersom denne har vært til attestering, vil den implisitt være underkjent nå.
     */
    data class Utfylt(
        val forrigeSteg: UnderBehandlingEllerTilAttestering,
        override val hendelseId: HendelseId,
        override val versjon: Hendelsesversjon,
        override val månedsvurderinger: Vurderinger,
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
        override val attesteringer: Attesteringshistorikk,
        override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo>,
    ) : UnderBehandling, KanEndres, UnderBehandlingEllerTilAttestering by forrigeSteg, ErUtfylt {

        constructor(
            forrigeSteg: TilbakekrevingsbehandlingTilAttestering,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) : this(
            forrigeSteg = forrigeSteg,
            hendelseId = hendelseId,
            versjon = versjon,
            månedsvurderinger = forrigeSteg.månedsvurderinger,
            vedtaksbrevvalg = forrigeSteg.vedtaksbrevvalg,
            attesteringer = forrigeSteg.attesteringer,
            forhåndsvarselsInfo = forrigeSteg.forhåndsvarselsInfo,
        )

        override val erUnderkjent = attesteringer.isNotEmpty()
        override fun leggTilVurderinger(
            månedsvurderinger: Vurderinger,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = this.copy(
            hendelseId = hendelseId,
            månedsvurderinger = månedsvurderinger,
            versjon = versjon,
        )

        override fun oppdaterVedtaksbrev(
            vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = this.copy(
            hendelseId = hendelseId,
            vedtaksbrevvalg = vedtaksbrevvalg,
            versjon = versjon,
        )

        override fun leggTilForhåndsvarselDokumentId(
            dokumentId: UUID,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
            hendelsesTidspunkt: Tidspunkt,
        ) = this.copy(
            hendelseId = hendelseId,
            forhåndsvarselsInfo = this.forhåndsvarselsInfo.plus(
                ForhåndsvarselMetaInfo(dokumentId, hendelsesTidspunkt),
            ),
            versjon = versjon,
        )

        override fun erÅpen() = true
        override fun oppdaterKravgrunnlag(
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
            nyttKravgrunnlag: Kravgrunnlag,
        ): Påbegynt {
            if (this.kravgrunnlag.eksternKravgrunnlagId == nyttKravgrunnlag.eksternKravgrunnlagId) {
                throw IllegalStateException("Prøvde å oppdatere kravgrunnlag for behandling ${this.id}, men kravgrunnlags-id'en er lik")
            }

            return Påbegynt(
                hendelseId = hendelseId,
                versjon = versjon,
                månedsvurderinger = null,
                kravgrunnlag = nyttKravgrunnlag,
                forrigeSteg = this,
                vedtaksbrevvalg = this.vedtaksbrevvalg,
                forhåndsvarselsInfo = this.forhåndsvarselsInfo,
            )
        }
    }
}
