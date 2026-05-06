import json
import boto3

def lambda_handler(event, context):
    # CORS Headers para que el navegador no bloquee la respuesta
    headers = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-Api-Key',
        'Access-Control-Allow-Methods': 'OPTIONS,POST,DELETE'
    }
    
    # Comprobar qué método HTTP está usando el usuario
    method = event.get('httpMethod') or event.get('requestContext', {}).get('http', {}).get('method')

    try:
        # Extraer el cuerpo del mensaje de forma segura
        body_raw = event.get('body')
        if isinstance(body_raw, str):
            body = json.loads(body_raw)
        elif isinstance(body_raw, dict):
            body = body_raw
        else:
            body = event # Si el proxy está off, el evento es el body

        # Iniciamos el cliente de S3
        s3_client = boto3.client('s3')
        bucket_name = 'kronos-s3'

        # Borrar imagen
        if method == 'DELETE':
            file_name = body.get('file_name')
            
            # Validar que el nombre exista
            if not file_name:
                return {
                    'statusCode': 400,
                    'headers': headers,
                    'body': json.dumps({'error': 'Falta el parámetro file_name para borrar'})
                }

            # Eliminar el objeto de S3
            s3_client.delete_object(Bucket=bucket_name, Key=file_name)
            
            return {
                'statusCode': 200,
                'headers': headers,
                'body': json.dumps({'message': f'Archivo {file_name} eliminado correctamente'})
            }

        # Subir imagen
        else:
            file_name = body.get('file_name')
            file_type = body.get('file_type')
            
            # Validar que el nombre y el tipo de archivo existan
            if not file_name or not file_type:
                return {
                    'statusCode': 400, 
                    'headers': headers, 
                    'body': json.dumps({'error': 'Faltan parámetros file_name o file_type'})
                }
            
            # Generamos la URL pre-firmada válida por 5 minutos
            presigned_url = s3_client.generate_presigned_url(
                'put_object',
                Params={
                    'Bucket': bucket_name,
                    'Key': file_name,
                    'ContentType': file_type
                },
                ExpiresIn=300
            )
            
            # URL pública para guardar en la base de datos
            public_url = f"https://{bucket_name}.s3.amazonaws.com/{file_name}"
            
            # Devolver ambas URL. Una para subir la foto y otra para visualizarla después
            return {
                'statusCode': 200,
                'headers': headers,
                'body': json.dumps({
                    'upload_url': presigned_url,
                    'public_url': public_url
                })
            }
        
    except Exception as e:
        print(f"Error en la operación: {e}")
        return {
            'statusCode': 500, 
            'headers': headers, 
            'body': json.dumps({'error': str(e)})
        }
