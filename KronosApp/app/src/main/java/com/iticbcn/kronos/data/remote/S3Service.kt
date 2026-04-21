// S3Service.kt - Versión optimizada para Lambda Proxy Integration
package com.iticbcn.kronos.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object S3Service {
    private const val TAG = "S3Service"
    private const val BASE_URL = "https://4qctvrdnjc.execute-api.us-east-1.amazonaws.com/prod"
    private const val API_KEY = "jFOzFueL9B8RB9MIzrSJ08CMFq73tG0f8bbeHJHf"

    suspend fun uploadImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        var apiConn: HttpURLConnection? = null
        var s3Conn: HttpURLConnection? = null
        try {
            Log.d(TAG, "== INICIO SUBIDA (PROXY MODE) ==")

            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
            val token = user.getIdToken(true).await().token ?: return@withContext null

            // PASO 1: Obtener URLs de la API
            val fileName = "foto_${System.currentTimeMillis()}.jpg"
            val requestBody = Gson().toJson(mapOf("file_name" to fileName, "file_type" to "image/jpeg"))
            
            apiConn = (URL("$BASE_URL/fotos").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-Api-Key", API_KEY)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Connection", "close")
            }

            apiConn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val responseCode = apiConn.responseCode
            // Con Proxy Integration, el error real (400, 500) llegará aquí directamente
            val responseText = if (responseCode in 200..299) {
                apiConn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = apiConn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error $responseCode"
                Log.e(TAG, "Error de la API: $error")
                return@withContext null
            }

            Log.d(TAG, "Respuesta limpia de la API: $responseText")

            val gson = Gson()
            val data = gson.fromJson(responseText, Map::class.java)
            
            val uploadUrl = data["upload_url"] as? String
            val publicUrl = data["public_url"] as? String

            if (uploadUrl.isNullOrEmpty()) {
                Log.e(TAG, "Error: La API no devolvió upload_url")
                return@withContext null
            }

            // PASO 2: Subida al S3
            Log.d(TAG, "Subiendo al S3...")
            s3Conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 60000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "image/jpeg")
                setRequestProperty("Connection", "close")
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                s3Conn.outputStream.use { output -> input.copyTo(output) }
            }

            if (s3Conn.responseCode in 200..204) {
                Log.i(TAG, "¡SUBIDA EXITOSA! URL: $publicUrl")
                return@withContext publicUrl
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        } finally {
            apiConn?.disconnect()
            s3Conn?.disconnect()
        }
        return@withContext null
    }

    suspend fun synchronizeWithOracle(): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext false
            val token = user.getIdToken(true).await().token ?: return@withContext false
            conn = (URL("$BASE_URL/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-Api-Key", API_KEY)
                setRequestProperty("Connection", "close")
            }
            return@withContext conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
