
"""
@app.post("/partida", response_model=dict, tags=["4-POST PARTIDA"])
def addPartida(partida:PartidaRequest, db:Session = Depends(get_db)):
    result = add_partida_service(db, partida)
    return result
"""


from fastapi import FastAPI, Depends
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
import firebase_admin
from firebase_admin import credentials, firestore
from google.cloud.firestore import Client
import os

from models.Sector import SectorResponse
from services.service_sector import getSector

load_dotenv()

# Inicializar Firebase
cred = credentials.Certificate(os.getenv("FIREBASE_CREDENTIALS"))
firebase_admin.initialize_app(cred)

app = FastAPI()

# CORS para que el frontend pueda hacer peticiones
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"]
)

# Conexión a Firestore
def get_db():
    return firestore.client()

# GET sector por id
@app.get("/sector/{id_sector}", response_model=SectorResponse, tags=["Sector"])
def getSectorEndpoint(id_sector: str, db: Client = Depends(get_db)):
    return getSector(db, id_sector)