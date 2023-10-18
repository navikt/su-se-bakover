@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.vurdert.Månedsvurderinger
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
sealed interface UnderBehandling : KanLeggeTilBrev, KanVurdere, KanForhåndsvarsle {

    val forrigeSteg: KanEndres
    override val månedsvurderinger: Månedsvurderinger?
    override fun erÅpen() = true

    /**
     * Kan kun gå fra [OpprettetTilbakekrevingsbehandling] til [Påbegynt], men ikke tilbake til [OpprettetTilbakekrevingsbehandling].
     * Lovelige overganger er:
     *  * [AvbruttTilbakekrevingsbehandling]
     *  * [Påbegynt]
     *  * [Utfylt]
     */
    data class Påbegynt(
        override val forrigeSteg: KanEndres,
        override val hendelseId: HendelseId,
        override val månedsvurderinger: Månedsvurderinger?,
        override val forhåndsvarselDokumentIder: List<UUID>,
    ) : UnderBehandling, KanEndres by forrigeSteg {
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg? = null
        override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty()

        override fun erÅpen(): Boolean = true

        fun erVurdert(): Boolean = månedsvurderinger != null

        /**
         * Siden vedtaksbrevet er avhengig av månedsperiodene krever vi at månedsvurderingene er utfylt først.
         * Kan vurdere å gjøre Påbegynt til sealed og dele den opp i med og uten brev.
         */
        override fun oppdaterVedtaksbrev(
            hendelseId: HendelseId,
            vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
        ): Utfylt {
            return if (månedsvurderinger == null) {
                throw IllegalArgumentException("Må gjøre månedsvurderingene før man tar stilling til vedtaksbrev")
            } else {
                Utfylt(
                    forrigeSteg = forrigeSteg,
                    månedsvurderinger = månedsvurderinger,
                    hendelseId = hendelseId,
                    vedtaksbrevvalg = vedtaksbrevvalg,
                    attesteringer = forrigeSteg.attesteringer,
                    forhåndsvarselDokumentIder = forhåndsvarselDokumentIder,
                )
            }
        }

        override fun leggTilForhåndsvarselDokumentId(
            hendelseId: HendelseId,
            dokumentId: UUID,
        ): Påbegynt {
            return this.copy(
                hendelseId = hendelseId,
                forhåndsvarselDokumentIder = this.forhåndsvarselDokumentIder.plus(dokumentId),
            )
        }

        override fun leggTilVurderinger(
            hendelseId: HendelseId,
            månedsvurderinger: Månedsvurderinger,
        ) = this.copy(
            hendelseId = hendelseId,
            månedsvurderinger = månedsvurderinger,
        )
    }

    /**
     * Når vi først er utfylt, kan vi ikke gå tilbake til påbegynt.
     * Lovelige overganger er:
     *   *  [AvbruttTilbakekrevingsbehandling]
     *   * [TilbakekrevingsbehandlingTilAttestering]
     *
     *   @param forhåndsvarselDokumentIder Vi støtter og legge til nye forhåndsvarslinger selvom tilstanden er [Utfylt]
     */
    data class Utfylt(
        override val forrigeSteg: KanEndres,
        override val hendelseId: HendelseId,
        override val månedsvurderinger: Månedsvurderinger,
        override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
        override val attesteringer: Attesteringshistorikk,
        override val forhåndsvarselDokumentIder: List<UUID>,
    ) : UnderBehandling, KanEndres by forrigeSteg {

        override fun leggTilVurderinger(
            hendelseId: HendelseId,
            månedsvurderinger: Månedsvurderinger,
        ) = this.copy(
            hendelseId = hendelseId,
            månedsvurderinger = månedsvurderinger,
        )

        override fun oppdaterVedtaksbrev(
            hendelseId: HendelseId,
            vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
        ) = this.copy(
            hendelseId = hendelseId,
            vedtaksbrevvalg = vedtaksbrevvalg,
        )

        override fun leggTilForhåndsvarselDokumentId(
            hendelseId: HendelseId,
            dokumentId: UUID,
        ) = this.copy(
            hendelseId = hendelseId,
            forhåndsvarselDokumentIder = this.forhåndsvarselDokumentIder.plus(dokumentId),
        )

        override fun erÅpen() = true
    }
}
