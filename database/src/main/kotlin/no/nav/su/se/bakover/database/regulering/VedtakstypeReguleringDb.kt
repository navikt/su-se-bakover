package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor

enum class VedtakstypeReguleringDb {
    Endring,
    Regulering,
    ;

    fun toDomain(): ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype = when (this) {
        Endring -> ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring
        Regulering -> ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering
    }

    companion object {
        fun ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.toDb(): VedtakstypeReguleringDb = when (this) {
            ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring -> Endring
            ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering -> Regulering
        }
    }
}
