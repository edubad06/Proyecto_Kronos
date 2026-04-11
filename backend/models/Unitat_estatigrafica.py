from sqlmodel import SQLModel, Field

class Unitat_estatigrafica (SQLModel, table=True):
    id_ue : int = Field(default=None, primary_key=True)
    codi_ue: str
    color:str
    cronologia:str
    interpretacio:str
    material:str
    registrat_per:str
    textura:str
    tipus_ue:str
    descripcio: str

    codi_intervencio
    datacio
    longitud
    amplada
    alçada
    cota_sup
    cota_inf

    # sector
    # jaciment
    # privacitat
    #imatges_urls
    #relacions

    #RECORDAR A EDUARDO: codi vs id (en los 3) y quitar observaciones de ue y dejar solo descripcio

