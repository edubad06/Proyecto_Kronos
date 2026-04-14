from pydantic import BaseModel
from typing import Optional

class Sector(BaseModel):
    id_sector: str
    id_jaciment: str
    nom: str
    descripcio: Optional[str] = None
    privacitat: Optional[str] = "public"
    sincronitzat: Optional[bool] = False

class SectorResponse(BaseModel):
    id_sector: str
    id_jaciment: str
    nom: str
    descripcio: Optional[str] = None
    privacitat: Optional[str] = None

class SectorRequest(BaseModel):
    id_jaciment: str
    nom: str
    descripcio: Optional[str] = None
    privacitat: Optional[str] = "public"