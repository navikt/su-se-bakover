package no.nav.su.se.bakover.domain.søknadsbehandling

interface StatusovergangVisitor : SøknadsbehandlingVisitor {
    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Avslag) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Beregnet.Avslag) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Simulert) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.MedBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Underkjent.Avslag.UtenBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.UtenBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Avslag.MedBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.TilAttestering.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: LukketSøknadsbehandling) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    data class UgyldigStatusovergangException(
        private val søknadsbehandling: Any,
        private val statusovergang: Any,
        val msg: String = "Ugyldig statusovergang: ${statusovergang::class.qualifiedName} for type: ${søknadsbehandling::class.qualifiedName}",
    ) : RuntimeException(msg)
}
