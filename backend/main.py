from fastapi import FastAPI, Depends
from dotenv import load_dotenv
from sqlmodel import create_engine, Session, SQLModel, select

#from models.HistPartida import HistPartidaResponse, PartidaRequest
#from models.Idioma import IdiomaResponse
#from models.Usuario import UsuarioResponse
#from models.Palabra import PalabraResponse

#from services.service_palabra import getPalabra as get_palabra_service
#from services.service_idioma import getAbecedari as get_abecedari_service
#from services.service_usuario import getInstrucciones as get_instrucciones_service
#from services.service_historic import getHistoric as get_historic_service
#from services.service_historic import addPartida as add_partida_service
#from starlette.middleware.cors import CORSMiddleware

import os

app= FastAPI()
"""
Afegim el CORS per a que la tecnologia que s'utilitzi al frontend
pugui fer consultes als endpoints de la APIREST

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)
"""
#cargo variables de entorno
load_dotenv()
#configuro la conexión a la BD
DATABASE_URL = os.getenv("DATABASE_URL")
engine = create_engine(DATABASE_URL)
#creación de las tablas en la BD
SQLModel.metadata.create_all(engine)
#para poder conectar con la BD y llevar a cabo las acciones CRUD hay que crear las sessions:
def get_db():
    db = Session(engine)
    try:
        yield db
    finally:
        db.close()

#1- subministrar, al front-end, una paraula a endevinar
@app.get("/palabra/{idioma}", response_model = PalabraResponse, tags=["1-GET PALABRA"])
def getPalabra(idioma:str, db:Session = Depends(get_db)):
    result = get_palabra_service(db,idioma)
    return result

#2- subministrar, al front-end, les lletres de l’abecedari
@app.get("/idioma/{idioma}", response_model = IdiomaResponse, tags=["2-GET ABECEDARIO"])
def getAbecedario (idioma:str, db:Session = Depends(get_db)):
    result = get_abecedari_service(db,idioma)
    return result

#3- subministrar, al front-end, les instruccions
@app.get("/usuario/{idioma}", response_model = UsuarioResponse, tags=["3-GET INSTRUCCIONES"])
def getInstrucciones (idioma:str, db:Session = Depends(get_db)):
    result = get_instrucciones_service(db,idioma)
    return result
#4- subministrar, al front-end, l’historial de partida
@app.get ("/historico/{nombre}", response_model = HistPartidaResponse, tags=["4-GET PARTIDA"])
def getHistoricPartida (nombre:str, db:Session = Depends(get_db)):
    result = get_historic_service(db,nombre)
    return result
@app.post("/partida", response_model=dict, tags=["4-POST PARTIDA"])
def addPartida(partida:PartidaRequest, db:Session = Depends(get_db)):
    result = add_partida_service(db, partida)
    return result
