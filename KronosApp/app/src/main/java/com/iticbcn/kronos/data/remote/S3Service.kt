package com.iticbcn.kronos.data.remote

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object S3Service {
    private const val API_URL = "https://4qctvrdnjc.execute-api.us-east-1.amazonaws.com/prod"

    suspend fun uploadImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
            val token = user.getIdToken(false).await().token ?: return@withContext null

            // 1. Obtener upload_url y public_url
            val fileName = "foto_${System.currentTimeMillis()}.jpg"
            val requestBody = Gson().toJson(mapOf("file_name" to fileName, "file_type" to "image/jpeg"))

            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(requestBody) }

            if (conn.responseCode != 200) return@withContext null

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val responseMap = Gson().fromJson(response, Map::class.java)
            val uploadUrl = responseMap["upload_url"] as String
            val publicUrl = responseMap["public_url"] as String

            // 2. Subir el archivo mediante PUT a uploadUrl
            val putUrl = URL(uploadUrl)
            val putConn = putUrl.openConnection() as HttpURLConnection
            putConn.requestMethod = "PUT"
            putConn.doOutput = true
            putConn.setRequestProperty("Content-Type", "image/jpeg")

            context.contentResolver.openInputStream(uri)?.use { input ->
                putConn.outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            if (putConn.responseCode == 200 || putConn.responseCode == 201) {
                return@withContext publicUrl
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun synchronizeWithOracle(): Boolean = withContext(Dispatchers.IO) {
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext false
            val token = user.getIdToken(false).await().token ?: return@withContext false

            val url = URL(API_URL) // Asumiendo que es la misma base URL para sincronizar
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")

            return@withContext conn.responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }
}
