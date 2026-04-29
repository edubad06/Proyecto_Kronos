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

    /**
     * Sube una imagen a S3. 
     * @param authToken Opcional. Si se pasa, evita pedir un nuevo token a Firebase.
     */
    suspend fun uploadImage(context: Context, uri: Uri, authToken: String? = null): String? = withContext(Dispatchers.IO) {
        if (uri.scheme == "http" || uri.scheme == "https") return@withContext uri.toString()

        var apiConn: HttpURLConnection? = null
        var s3Conn: HttpURLConnection? = null
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
            val token = authToken ?: user.getIdToken(false).await().token ?: return@withContext null

            // PASO 1: Presigned URL
            val fileName = "foto_${System.currentTimeMillis()}_${(0..1000).random()}.jpg"
            val requestBody = Gson().toJson(mapOf("file_name" to fileName, "file_type" to "image/jpeg"))
            
            apiConn = (URL("$BASE_URL/fotos").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-Api-Key", API_KEY)
                setRequestProperty("Content-Type", "application/json")
            }

            apiConn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            if (apiConn.responseCode !in 200..299) {
                Log.e(TAG, "Error API Step 1: ${apiConn.responseCode}")
                return@withContext null
            }

            val responseText = apiConn.inputStream.bufferedReader().use { it.readText() }
            val data = Gson().fromJson(responseText, Map::class.java)
            val uploadUrl = data["upload_url"] as? String
            val publicUrl = data["public_url"] as? String

            if (uploadUrl.isNullOrEmpty()) return@withContext null

            // PASO 2: Upload to S3
            s3Conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 60000 
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "image/jpeg")
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                s3Conn.outputStream.use { output -> input.copyTo(output) }
            } ?: return@withContext null

            if (s3Conn.responseCode in 200..204) {
                return@withContext publicUrl
            }

        } catch (e: Exception) {
            Log.e(TAG, "Excepción en S3Service: ${e.message}")
        } finally {
            apiConn?.disconnect()
            s3Conn?.disconnect()
        }
        return@withContext null
    }

    suspend fun deleteImage(publicUrl: String): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext false
            val token = user.getIdToken(false).await().token ?: return@withContext false
            val fileName = publicUrl.substringAfterLast("/")
            val requestBody = Gson().toJson(mapOf("file_name" to fileName))

            conn = (URL("$BASE_URL/fotos").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-Api-Key", API_KEY)
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            return@withContext conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
