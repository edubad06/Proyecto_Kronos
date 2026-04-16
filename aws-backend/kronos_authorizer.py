import json
import boto3
import firebase_admin
from firebase_admin import credentials, auth as firebase_auth

# Obtener credenciales de Firebase desde AWS Secrets Manager
def get_firebase_secret():
    secret_name = "kronos/firebase/credentials"
    client = boto3.client(service_name='secretsmanager')
    response = client.get_secret_value(SecretId=secret_name)
    return json.loads(response['SecretString'])

# Inicializar Firebase
if not firebase_admin._apps:
    secret_json = get_firebase_secret()
    cred = credentials.Certificate(secret_json)
    firebase_admin.initialize_app(cred)

# Crear política que API Gateway necesita para dejar pasar al usuario
def generate_policy(principal_id, effect, resource):
    return {
        'principalId': principal_id,
        'policyDocument': {
            'Version': '2012-10-17',
            'Statement': [{
                'Action': 'execute-api:Invoke',
                'Effect': effect,
                'Resource': resource
            }]
        }
    }

def lambda_handler(event, context):
    # Recuperar el token enviado desde el cliente
    token = event.get('authorizationToken', '')
    
    # Quitar el prefijo 'Bearer ' para tener solo el código del token
    if token.startswith('Bearer '):
        token = token.split(' ')[1]
        
    try:
        # Firebase comprueba si el token es válido
        decoded_token = firebase_auth.verify_id_token(token)
        uid = decoded_token['uid'] # ID del usuario logueado
        
        # Si no hay error, el token es válido. Generamos política Allow
        print(f"Acceso permitido al usuario: {uid}")
        return generate_policy(uid, 'Allow', event['methodArn'])
        
    except Exception as e:
        # Si hay error, el token es inválido. Generamos política Deny
        print(f"Acceso denegado. Token inválido: {e}")
        # Generamos política Deny
        return generate_policy('unauthorized', 'Deny', event['methodArn'])