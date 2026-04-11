from sqlmodel import SQLModel, Field

class Jaciment (SQLModel, table=True):
    id_jaciment : int = Field(default=None, primary_key=True)
    codi_jaciment: str
    director:str
    coordenada_x: int #altitud
    coordenada_y: int #latitud
    coordenada_z: int #profundidad
    descripcio: str

    # mapa
    # privacitat
    # imatges
    # permisos / asignar editores: Usuari 1, usuari 2, etc
