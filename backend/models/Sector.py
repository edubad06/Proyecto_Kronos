from sqlmodel import SQLModel, Field

class Sector (SQLModel, table=True):
    id_sector : int = Field(default=None, primary_key=True)
    codi_sector: str
    descripcio: str

    #jaciment ->foránea?
    #privacitat
    #imatges
