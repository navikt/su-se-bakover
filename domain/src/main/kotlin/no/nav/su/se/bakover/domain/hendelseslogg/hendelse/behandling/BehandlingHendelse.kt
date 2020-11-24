package no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling

import com.fasterxml.jackson.annotation.JsonTypeName
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.AbstractHendelse

sealed class BehandlingHendelse : AbstractHendelse()

@JsonTypeName(value = "UnderkjentAttestering")
data class UnderkjentAttestering(
    val attestant: String,
    val begrunnelse: String,
    override val tidspunkt: Tidspunkt = Tidspunkt.now()
) : BehandlingHendelse() {
    override val overskrift: String = "Attestering underkjent"
    override val underoverskrift: String = "$tidspunkt - $attestant"
    override val melding: String = begrunnelse
}
