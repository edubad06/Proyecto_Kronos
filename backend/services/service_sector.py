from google.cloud.firestore import Client
from fastapi import HTTPException, status
from crud.crud_sector import get_sector

def getSector(db: Client, id_sector: str):
    try:
        sector = get_sector(db, id_sector)
        if not sector:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Sector no trobat"
            )
        return sector

    except HTTPException:
        raise

    except Exception:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Error intern del servidor"
        )