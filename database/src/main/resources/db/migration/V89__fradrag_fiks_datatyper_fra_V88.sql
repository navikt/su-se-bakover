update grunnlag_fradrag set
    fradragstype = trim(both '"' from fradragstype)
    tilhører = trim(both '"' from tilhører);