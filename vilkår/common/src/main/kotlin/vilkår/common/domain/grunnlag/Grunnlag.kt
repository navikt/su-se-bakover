package vilkår.common.domain.grunnlag

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import java.util.UUID

interface Grunnlag {
    val id: UUID
    val opprettet: Tidspunkt
    val periode: Periode

    fun tilstøter(other: Grunnlag) = this.periode.tilstøter(other.periode)

    /** unnlater å sjekke på ID, opprettet og periode */
    fun erLik(other: Grunnlag): Boolean

    fun tilstøterOgErLik(other: Grunnlag) = this.tilstøter(other) && this.erLik(other)
    fun copyWithNewId(): Grunnlag
}

/**
 * Listen med periode trenger ikke være sammenhengende eller sortert.
 * Den kan og inneholde duplikater.
 */
fun NonEmptyList<Grunnlag>.periode(): Periode = this.map { it.periode }.minAndMaxOf()
