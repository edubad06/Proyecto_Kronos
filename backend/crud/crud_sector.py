from google.cloud.firestore import Client

def get_sector(db: Client, id_sector: str):
    doc = db.collection("sectors").document(id_sector).get()
    if doc.exists:
        return doc.to_dict()
    return None