package no.nav.su.se.bakover.domain.klage

import java.util.UUID

/**
 * Inneholder kun selve vilkårsvurderingene som er gjort i forbindelse med en klage.
 * For selve klagen se [VilkårsvurdertKlage]
 */
sealed class VilkårsvurderingerTilKlage {

    abstract val vedtakId: UUID?
    abstract val innenforFristen: Boolean?
    abstract val klagesDetPåKonkreteElementerIVedtaket: Boolean?
    abstract val erUnderskrevet: Boolean?
    abstract val begrunnelse: String?

    data class Påbegynt(
        override val vedtakId: UUID?,
        override val innenforFristen: Boolean?,
        override val klagesDetPåKonkreteElementerIVedtaket: Boolean?,
        override val erUnderskrevet: Boolean?,
        override val begrunnelse: String?,
    ) : VilkårsvurderingerTilKlage()

    data class Ferdig(
        override val vedtakId: UUID,
        override val innenforFristen: Boolean,
        override val klagesDetPåKonkreteElementerIVedtaket: Boolean,
        override val erUnderskrevet: Boolean,
        override val begrunnelse: String,
    ) : VilkårsvurderingerTilKlage()
}
