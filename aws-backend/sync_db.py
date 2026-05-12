import boto3
import json
import os
import firebase_admin
from firebase_admin import credentials, firestore
import oracledb

# Obtener credenciales de Firebase desde AWS Secrets Manager
def get_firebase_secret():
    secret_name = "kronos/firebase/credentials"
    client = boto3.client(service_name='secretsmanager')
    response = client.get_secret_value(SecretId=secret_name)
    return json.loads(response['SecretString'])

def lambda_handler(event, context):
    # Inicializar Firebase
    if not firebase_admin._apps:
        secret_json = get_firebase_secret()
        cred = credentials.Certificate(secret_json)
        firebase_admin.initialize_app(cred)
    
    db_firestore = firestore.client()
    
    try:
        # Conexión a Oracle
        conn = oracledb.connect(
            user=os.environ.get('DB_USER'),
            password=os.environ.get('DB_PASS'),
            dsn=os.environ.get('DB_DSN')
        )
        cursor = conn.cursor()
        resum_sync = {"jaciments": 0, "sectors": 0, "ues": 0, "fotos": 0, "relacions": 0}

        # Procesar yacimientos
        # Buscar registros en Firebase marcados como 'sincronitzat: False'
        docs_jac = db_firestore.collection('jaciments').where('sincronitzat', '==', False).get()
        
        # SQL para insertar si no existe o actualizar si ya existe
        sql_jac = """
        MERGE INTO JACIMENTS t 
        USING (SELECT :v_codi as codi FROM dual) s ON (t.codi_jaciment = s.codi)
        WHEN MATCHED THEN UPDATE SET t.nom = :v_nom, t.director = :v_director, t.coordenada_x = :v_x, t.coordenada_y = :v_y, t.coordenada_z = :v_z, t.descripcio = :v_descripcio, t.data = :v_data
        WHEN NOT MATCHED THEN INSERT (codi_jaciment, nom, director, coordenada_x, coordenada_y, coordenada_z, descripcio, data) 
        VALUES (:v_codi, :v_nom, :v_director, :v_x, :v_y, :v_z, :v_descripcio, :v_data)
        """
        sql_del_foto_jac = "DELETE FROM fotografies_jaciment WHERE id_jaciment = (SELECT id_jaciment FROM JACIMENTS WHERE codi_jaciment = :v_codi)"
        sql_ins_foto_jac = "INSERT INTO fotografies_jaciment (id_jaciment, url_imatge) VALUES ((SELECT id_jaciment FROM JACIMENTS WHERE codi_jaciment = :v_codi), :v_url)"

        for doc in docs_jac:
            p = doc.to_dict()
            try:
                codi_jac = p.get('codi_jaciment')
                cursor.execute(sql_jac, {
                    "v_codi": codi_jac,
                    "v_nom": p.get('nom'),
                    "v_director": p.get('director'),
                    "v_x": p.get('coordenada_x'),
                    "v_y": p.get('coordenada_y'),
                    "v_z": p.get('coordenada_z'),
                    "v_descripcio": p.get('descripcio'),
                    "v_data": p.get('data')
                })
                
                # Actualizar las fotos asociadas al yacimiento
                cursor.execute(sql_del_foto_jac, {"v_codi": codi_jac})
                for url in p.get('imatges_urls', []):
                    cursor.execute(sql_ins_foto_jac, {"v_codi": codi_jac, "v_url": url})
                    resum_sync["fotos"] += 1

                # Marca el documento como sincronizado en Firebase    
                doc.reference.update({'sincronitzat': True})
                resum_sync["jaciments"] += 1
            except Exception as e:
                print(f"Error Jaciment {p.get('codi_jaciment')}: {e}")

        # Procesar sectores
        docs_s = db_firestore.collection('sectors').where('sincronitzat', '==', False).get()
        
        # SQL para insertar si no existe o actualizar si ya existe
        sql_sector = """
        MERGE INTO SECTORS t 
        USING (SELECT :v_codi as codi FROM dual) s ON (t.codi_sector = s.codi)
        WHEN MATCHED THEN UPDATE SET t.id_jaciment = (SELECT id_jaciment FROM JACIMENTS WHERE codi_jaciment = :v_codi_jac), t.nom = :v_nom, t.descripcio = :v_descripcio, t.data = :v_data, t.registrat_per = :v_reg
        WHEN NOT MATCHED THEN INSERT (codi_sector, id_jaciment, nom, descripcio, data, registrat_per) 
        VALUES (:v_codi, (SELECT id_jaciment FROM JACIMENTS WHERE codi_jaciment = :v_codi_jac), :v_nom, :v_descripcio, :v_data, :v_reg)
        """
        sql_del_foto_sec = "DELETE FROM fotografies_sector WHERE id_sector = (SELECT id_sector FROM SECTORS WHERE codi_sector = :v_codi)"
        sql_ins_foto_sec = "INSERT INTO fotografies_sector (id_sector, url_imatge) VALUES ((SELECT id_sector FROM SECTORS WHERE codi_sector = :v_codi), :v_url)"

        for doc in docs_s:
            p = doc.to_dict()
            try:
                codi_sec = p.get('codi_sector')
                cursor.execute(sql_sector, {
                    "v_codi": codi_sec, 
                    "v_codi_jac": p.get('codi_jaciment'), 
                    "v_nom": p.get('nom'),
                    "v_descripcio": p.get('descripcio'),
                    "v_data": p.get('data'),
                    "v_reg": p.get('registrat_per')
                })
                
                # Actualizar las fotos asociadas al sector
                cursor.execute(sql_del_foto_sec, {"v_codi": codi_sec})
                for url in p.get('imatges_urls', []):
                    cursor.execute(sql_ins_foto_sec, {"v_codi": codi_sec, "v_url": url})
                    resum_sync["fotos"] += 1
                
                # Marca el documento como sincronizado en Firebase
                doc.reference.update({'sincronitzat': True})
                resum_sync["sectors"] += 1
            except Exception as e:
                print(f"Error Sector {p.get('codi_sector')}: {e}")

        # Procesar UE
        docs_ue = db_firestore.collection('unitats_estratigrafiques').where('sincronitzat', '==', False).get()
        
        sql_ue = """
        MERGE INTO UNITATS_ESTRATIGRAFIQUES t 
        USING (SELECT :v_codi as codi FROM dual) s ON (t.codi_ue = s.codi)
        WHEN MATCHED THEN UPDATE SET t.id_sector = (SELECT id_sector FROM SECTORS WHERE codi_sector = :v_codi_sec), 
            t.tipus_ue = :v_tipus, t.descripcio = :v_descripcio, t.material = :v_mat, t.estat_conservacio = :v_estat, 
            t.cronologia = :v_crono, t.textura = :v_textura, t.color = :v_color, 
            t.coordenada_x = :v_x, t.coordenada_y = :v_y, t.coordenada_z = :v_z, t.data = :v_data, t.registrat_per = :v_reg
        WHEN NOT MATCHED THEN INSERT (codi_ue, id_sector, tipus_ue, descripcio, material, estat_conservacio, cronologia, textura, color, coordenada_x, coordenada_y, coordenada_z, data, registrat_per) 
            VALUES (:v_codi, (SELECT id_sector FROM SECTORS WHERE codi_sector = :v_codi_sec), :v_tipus, :v_descripcio, :v_mat, :v_estat, :v_crono, :v_textura, :v_color, :v_x, :v_y, :v_z, :v_data, :v_reg)
        """
        
        sql_delete_fotos = "DELETE FROM fotografies_ue WHERE id_ue = (SELECT id_ue FROM UNITATS_ESTRATIGRAFIQUES WHERE codi_ue = :v_codi)"
        sql_insert_foto = "INSERT INTO fotografies_ue (id_ue, url_imatge) VALUES ((SELECT id_ue FROM UNITATS_ESTRATIGRAFIQUES WHERE codi_ue = :v_codi), :v_url)"

        sql_delete_relacions = "DELETE FROM relacions_ue WHERE id_ue_origen = (SELECT id_ue FROM UNITATS_ESTRATIGRAFIQUES WHERE codi_ue = :v_codi)"
        sql_insert_relacio = "INSERT INTO relacions_ue (id_ue_origen, tipus_relacio, id_ue_desti) VALUES ((SELECT id_ue FROM UNITATS_ESTRATIGRAFIQUES WHERE codi_ue = :v_origen), :v_tipus, :v_desti)"

        for doc in docs_ue:
            p = doc.to_dict()
            try:
                codi_ue_actual = p.get('codi_ue')
                
                # Gestionar los datos de la UE
                cursor.execute(sql_ue, {
                    "v_codi": codi_ue_actual,
                    "v_codi_sec": p.get('codi_sector'),
                    "v_tipus": p.get('tipus_ue'),
                    "v_descripcio": p.get('descripcio'),
                    "v_mat": p.get('material'),        
                    "v_estat": p.get('estat_conservacio'), 
                    "v_crono": p.get('cronologia'),
                    "v_textura": p.get('textura'),
                    "v_color": p.get('color'),
                    "v_x": p.get('x'),
                    "v_y": p.get('y'),
                    "v_z": p.get('z'),
                    "v_data": p.get('data'),
                    "v_reg": p.get('registrat_per')
                })

                # Gestionar las fotos de la UE
                imatges = p.get('imatges_urls', [])
                cursor.execute(sql_delete_fotos, {"v_codi": codi_ue_actual})
                for url in imatges:
                    cursor.execute(sql_insert_foto, {"v_codi": codi_ue_actual, "v_url": url})
                    resum_sync["fotos"] += 1
                    
                # Gestionar las relaciones estratigráficas
                cursor.execute(sql_delete_relacions, {"v_codi": codi_ue_actual})
                for rel in p.get('relacions', []):
                    cursor.execute(sql_insert_relacio, {
                        "v_origen": codi_ue_actual, 
                        "v_tipus": rel.get('tipus'), 
                        "v_desti": rel.get('desti')
                    })
                    resum_sync["relacions"] += 1

                # Marcar com a sincronitzat
                doc.reference.update({'sincronitzat': True})
                resum_sync["ues"] += 1
            except Exception as e:
                print(f"Error UE {p.get('codi_ue')}: {e}")

        # Cerrar conexión
        conn.commit()
        cursor.close()
        conn.close()
        
        # Cabeceras CORS para permitir la respuesta a la web
        cors_headers = {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-Api-Key',
            'Access-Control-Allow-Methods': 'OPTIONS,POST,GET,ANY'
        }
        
        mensaje = f"Sincronitzat: {resum_sync['jaciments']} Jaciments, {resum_sync['sectors']} Sectors, {resum_sync['ues']} UEs, {resum_sync['fotos']} Fotos i {resum_sync['relacions']} Relacions."
        return {
            'statusCode': 200, 
            'headers': cors_headers,
            'body': json.dumps({"missatge": mensaje})
        }

    except Exception as e:
        print(f"Error crític: {e}")
        return {
            'statusCode': 500, 
            'headers': {
                'Access-Control-Allow-Origin': '*',
                'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-Api-Key',
                'Access-Control-Allow-Methods': 'OPTIONS,POST,GET,ANY'
            },
            'body': json.dumps({"error": f"Error intern del servidor: {str(e)}"})
        }