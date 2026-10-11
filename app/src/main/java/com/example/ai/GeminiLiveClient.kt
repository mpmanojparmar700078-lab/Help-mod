package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(val text: String, val screenObservation: String? = null) : GeminiResult()
    data class Error(val errorMessage: String, val errorCode: String? = null) : GeminiResult()
}

class GeminiLiveClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeScreenAndRespond(
        apiKey: String,
        screenBitmap: Bitmap?,
        userQuestion: String,
        conversationHistory: List<Pair<String, String>> = emptyList(), // role, text
        explanationDetail: String = "concise",
        language: String = "hi-IN"
    ): GeminiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiResult.Error(
                "Gemini API Key उपलब्ध नहीं है। कृपया ऐप सेटिंग्स में अपनी API Key दर्ज करें या Secrets पैनल में GEMINI_API_KEY सेट करें।"
            )
        }

        try {
            val root = JSONObject()

            // System Instruction in Hindi
            val detailInstruction = if (explanationDetail == "detailed") {
                "विस्तार से चरण-दर-चरण समझाएं।"
            } else {
                "उत्तर संक्षिप्त (2-4 वाक्य), स्पष्ट और तुरंत समझ आने योग्य रखें ताकि आवाज़ में सुनना आसान हो।"
            }

            val langInstruction = when (language) {
                "en-US" -> "Respond strictly in clear, friendly English."
                "hi-en" -> "Respond in natural conversational Hinglish (Hindi written in Roman or Hindi mixed with English terms)."
                else -> "हमेशा स्वाभाविक, सरल और शुद्ध हिन्दी (Devanagari script) में उत्तर दें।"
            }

            val systemPrompt = """
                आप 'Live Screen AI Guide' (लाइव स्क्रीन एआई गाइड) हैं — उपयोगकर्ता के फ़ोन की स्क्रीन देखकर हिन्दी में तुरंत मार्गदर्शन देने वाले सहायक।
                
                मुख्य निर्देश:
                1. उपयोगकर्ता अपनी स्क्रीन साझा कर रहे हैं। स्क्रीन के दृश्य (टेक्स्ट, बटन, एरर, आइकन, सेटिंग्स) का बारीकी से विश्लेषण करें।
                2. $langInstruction
                3. $detailInstruction
                4. मार्गदर्शन केवल सूचनात्मक है: आप स्वयं स्क्रीन पर टैप, स्वाइप या कोई बदलाव नहीं कर सकते। उपयोगकर्ता को बताएं कि उन्हें क्या करना चाहिए (उदाहरण: 'नीचे 'कन्फर्म' बटन पर टैप करें', 'ऊपर दाएं कोने में गियर आइकन पर जाएं')।
                5. स्क्रीन पर दिखाई देने वाले किसी भी पासवर्ड, कार्ड नंबर या निजी पिन को आवाज़ में न दोहराएं।
                6. यदि स्क्रीन पर कोई स्पष्ट एरर है, तो एरर का कारण और समाधान सरल शब्दों में समझाएं।
                7. यदि स्क्रीन अस्पष्ट है या कुछ नहीं दिख रहा है, तो ईमानदारी से बताएं।
            """.trimIndent()

            val systemInstructionObj = JSONObject().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                }
                put("parts", partsArray)
            }
            root.put("systemInstruction", systemInstructionObj)

            // Contents array
            val contentsArray = JSONArray()

            // Include last 3 conversation turns for context
            val recentTurns = conversationHistory.takeLast(6)
            for ((role, text) in recentTurns) {
                val contentObj = JSONObject().apply {
                    put("role", if (role == "user") "user" else "model")
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    }
                    put("parts", parts)
                }
                contentsArray.put(contentObj)
            }

            // Current prompt with optional screen image
            val currentTurn = JSONObject().apply {
                put("role", "user")
                val parts = JSONArray()

                // If we have a screen frame, attach it as inlineData
                if (screenBitmap != null && !screenBitmap.isRecycled) {
                    val base64Image = bitmapToBase64(screenBitmap)
                    if (base64Image.isNotEmpty()) {
                        val inlineData = JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        }
                        parts.put(JSONObject().apply { put("inlineData", inlineData) })
                    }
                }

                val promptText = if (userQuestion.isNotBlank()) {
                    userQuestion
                } else {
                    "मेरी स्क्रीन पर अभी क्या दिखाई दे रहा है और मुझे यहाँ क्या करना चाहिए?"
                }
                parts.put(JSONObject().apply { put("text", promptText) })

                put("parts", parts)
            }
            contentsArray.put(currentTurn)
            root.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject().apply {
                put("temperature", 0.4)
                put("topP", 0.9)
                put("topK", 32)
            }
            root.put("generationConfig", genConfig)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val body = root.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorObj = runCatching { JSONObject(responseBody).optJSONObject("error") }.getOrNull()
                    val errorMsg = errorObj?.optString("message") ?: "HTTP ${response.code}: $responseBody"
                    return@withContext GeminiResult.Error(
                        errorMessage = "Gemini API त्रुटि (${response.code}): $errorMsg",
                        errorCode = response.code.toString()
                    )
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext GeminiResult.Error("मॉडल से कोई उत्तर प्राप्त नहीं हुआ।")
                }

                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")

                val textBuilder = StringBuilder()
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val text = part.optString("text")
                        if (text.isNotBlank()) {
                            textBuilder.append(text)
                        }
                    }
                }

                val resultText = textBuilder.toString().trim()
                if (resultText.isEmpty()) {
                    GeminiResult.Error("स्क्रीन विश्लेषण में रिक्त उत्तर प्राप्त हुआ।")
                } else {
                    GeminiResult.Success(resultText)
                }
            }
        } catch (e: Exception) {
            GeminiResult.Error("नेटवर्क या कनेक्शन त्रुटि: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        return try {
            val outputStream = ByteArrayOutputStream()
            // Compress as JPEG at 75% quality for quick upload & token efficiency
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }
}
