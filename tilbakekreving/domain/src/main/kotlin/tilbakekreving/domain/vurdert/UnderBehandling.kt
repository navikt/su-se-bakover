@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.NonBlankString
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselMetaInfo
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdert.VurderingerMedKrav
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
    KanLeggeTilNotat,
    UnderBehandlingEllerTilAttestering {

    override val vurderingerMedKrav: VurderingerMedKrav?
    val erUnderkjent: Boolean

    override fun erÅpen() = true

    override fun oppdaterNotat(
        notat: NonBlankString?,
        hendelseId: HendelseId,
        versjon: Hendelsesversjon,
    ): UnderBehandling =
        when (this) {
            is Påbegynt -> this.copy(hendelseId = hendelseId, versjon = versjon, notat = notat)
            is Utfylt -> this.copy(hendelseId = hendelseId, versjon = versjon, notat = notat)
        }

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
        override val vurderingerMedKrav: VurderingerMedKrav?,
        override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo>,
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = null,
        override val kravgrunnlag: Kravgrunnlag,
        override val erKravgrunnlagUtdatert: Boolean,
        override val notat: NonBlankString?,
    ) : UnderBehandling, KanEndres by forrigeSteg {
        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()

        // Behandlingen må være utfylt før man kan attestere/underkjenne.
        override val erUnderkjent = false
        override fun erÅpen(): Boolean = true

        init {
            // TODO jah: Kan vi type oss ut av dette?
            require(forrigeSteg is OpprettetTilbakekrevingsbehandling || forrigeSteg is Påbegynt || forrigeSteg is KanOppdatereKravgrunnlag)
        }

        fun erVurdert(): Boolean = vurderingerMedKrav != null

        /**
         * Siden vedtaksbrevet er avhengig av månedsperiodene krever vi at månedsvurderingene er utfylt først.
         * Kan vurdere å gjøre Påbegynt til sealed og dele den opp i med og uten brev.
         */
        override fun oppdaterVedtaksbrev(
            vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ): Utfylt {
            return if (vurderingerMedKrav == null) {
                throw IllegalArgumentException("Må gjøre månedsvurderingene før man tar stilling til vedtaksbrev")
            } else {
                Utfylt(
                    forrigeSteg = this,
                    vurderingerMedKrav = vurderingerMedKrav,
                    hendelseId = hendelseId,
                    vedtaksbrevvalg = vedtaksbrevvalg,
                    attesteringer = forrigeSteg.attesteringer,
                    forhåndsvarselsInfo = forhåndsvarselsInfo,
                    versjon = versjon,
                    notat = notat,
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

        override fun leggTilVurderinger(
            månedsvurderinger: VurderingerMedKrav,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = this.copy(
            vurderingerMedKrav = månedsvurderinger,
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
        override val vurderingerMedKrav: VurderingerMedKrav,
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
        override val attesteringer: Attesteringshistorikk,
        override val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfo>,
        override val notat: NonBlankString?,
    ) : UnderBehandling, KanEndres, UnderBehandlingEllerTilAttestering by forrigeSteg, ErUtfylt {

        constructor(
            forrigeSteg: TilbakekrevingsbehandlingTilAttestering,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) : this(
            forrigeSteg = forrigeSteg,
            hendelseId = hendelseId,
            versjon = versjon,
            vurderingerMedKrav = forrigeSteg.vurderingerMedKrav,
            vedtaksbrevvalg = forrigeSteg.vedtaksbrevvalg,
            attesteringer = forrigeSteg.attesteringer,
            forhåndsvarselsInfo = forrigeSteg.forhåndsvarselsInfo,
            notat = forrigeSteg.notat,
        )

        override val erUnderkjent = attesteringer.isNotEmpty()
        override fun leggTilVurderinger(
            månedsvurderinger: VurderingerMedKrav,
            hendelseId: HendelseId,
            versjon: Hendelsesversjon,
        ) = this.copy(
            hendelseId = hendelseId,
            vurderingerMedKrav = månedsvurderinger,
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
    }
}
