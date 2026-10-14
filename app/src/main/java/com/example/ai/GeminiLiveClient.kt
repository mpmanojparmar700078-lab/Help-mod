package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
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
    companion object {
        private const val TAG = "GeminiLiveClient"
        // Compliant, supported multimodal vision models for Gemini API
        const val PRIMARY_MODEL = "gemini-flash-latest"
        const val FALLBACK_MODEL = "gemini-3.5-flash"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank() || trimmedKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key is missing or default placeholder")
            return@withContext GeminiResult.Error(
                "Gemini API Key उपलब्ध नहीं है। कृपया ऐप सेटिंग्स (Settings) में अपनी Google AI Studio API Key दर्ज करें।",
                "MISSING_KEY"
            )
        }

        val hasValidFrame = screenBitmap != null && !screenBitmap.isRecycled
        Log.d(TAG, "Starting screen analysis. Frame attached: $hasValidFrame, Question: '$userQuestion'")

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
                आप 'Live Screen AI Guide' (लाइव स्क्रीन एआई गाइड) हैं — उपयोगकर्ता के फ़ोन की स्क्रीन देखकर हिन्दी में तुरंत सटीक मार्गदर्शन देने वाले विशेषज्ञ सहायक।
                
                मुख्य निर्देश:
                1. उपयोगकर्ता अपनी स्क्रीन साझा कर रहे हैं। स्क्रीन के दृश्य (दिखाई देने वाले टेक्स्ट, बटन, एरर डायलॉग, आइकन, सेटिंग्स, ऐप्स) का बारीकी से विश्लेषण करें।
                2. $langInstruction
                3. $detailInstruction
                4. मार्गदर्शन केवल सूचनात्मक है: आप स्वयं स्क्रीन पर टैप या स्वाइप नहीं कर सकते। उपयोगकर्ता को स्पष्ट रूप से बताएं कि उन्हें क्या करना चाहिए (उदाहरण: 'स्क्रीन के नीचे 'कन्फर्म' बटन पर टैप करें', 'ऊपर दाएं कोने में गियर आइकन पर जाएं')।
                5. सुरक्षा नियम: स्क्रीन पर दिखाई देने वाले किसी भी पासवर्ड, क्रेडिट कार्ड नंबर या निजी पिन को कभी भी आवाज़ में न दोहराएं।
                6. यदि स्क्रीन पर कोई एरर संदेश दिखाई दे रहा है, तो एरर का सही कारण और समाधान सरल शब्दों में बताएं।
                7. यदि स्क्रीन छवि उपलब्ध नहीं है, तो उपयोगकर्ता को बताएं कि स्क्रीन शेयरिंग चालू करें।
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

            // Include recent conversation turns for context
            val recentTurns = conversationHistory.takeLast(4)
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

            // Current turn with screen screenshot and user prompt
            val currentTurn = JSONObject().apply {
                put("role", "user")
                val parts = JSONArray()

                // If screen frame is present, attach as inlineData JPEG
                if (hasValidFrame) {
                    val base64Image = bitmapToBase64(screenBitmap!!)
                    if (base64Image.isNotEmpty()) {
                        Log.d(TAG, "Screen frame encoded to Base64 (length: ${base64Image.length} chars, dimensions: ${screenBitmap.width}x${screenBitmap.height})")
                        val inlineData = JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        }
                        parts.put(JSONObject().apply { put("inlineData", inlineData) })
                    } else {
                        Log.w(TAG, "Failed to compress bitmap to base64")
                    }
                } else {
                    Log.d(TAG, "No screen frame available for this request")
                }

                val promptText = if (hasValidFrame) {
                    if (userQuestion.isNotBlank()) {
                        "उपयोगकर्ता का प्रश्न: $userQuestion\n\n(कृपया संलग्न स्क्रीनशॉट को ध्यान से देखें। स्क्रीन पर मौजूद मुख्य टेक्स्ट, बटन, आइकन या त्रुटि संदेश का सीधे संदर्भ देते हुए व्यावहारिक मार्गदर्शन दें।)"
                    } else {
                        "मेरी वर्तमान स्क्रीन का विश्लेषण करें: अभी स्क्रीन पर क्या खुला है और मुझे यहाँ क्या करना चाहिए?"
                    }
                } else {
                    if (userQuestion.isNotBlank()) {
                        userQuestion
                    } else {
                        "स्क्रीन शेयरिंग अभी बंद है। कृपया मुझे बताएं कि स्क्रीन शेयरिंग कैसे शुरू करें।"
                    }
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

            // Try primary model (gemini-flash-latest), fallback to gemini-3.5-flash
            var result = executeGenerateContent(PRIMARY_MODEL, trimmedKey, root)
            if (result is GeminiResult.Error && (result.errorCode == "404" || result.errorCode == "400")) {
                Log.w(TAG, "Primary model $PRIMARY_MODEL returned ${result.errorCode}, trying fallback to $FALLBACK_MODEL")
                result = executeGenerateContent(FALLBACK_MODEL, trimmedKey, root)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in analyzeScreenAndRespond: ${e.message}", e)
            GeminiResult.Error("नेटवर्क या कनेक्शन त्रुटि: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun executeGenerateContent(model: String, apiKey: String, requestPayload: JSONObject): GeminiResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val body = requestPayload.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.d(TAG, "Response code: ${response.code} from model $model")

                if (!response.isSuccessful) {
                    val errorObj = runCatching { JSONObject(responseBody).optJSONObject("error") }.getOrNull()
                    val errorMsg = errorObj?.optString("message") ?: "HTTP ${response.code}: $responseBody"
                    val errorStatus = errorObj?.optString("status") ?: response.code.toString()

                    Log.e(TAG, "API Error: HTTP ${response.code}, status: $errorStatus, message: $errorMsg")

                    val friendlyMessage = when (response.code) {
                        400 -> "अनुरोध अमान्य है: $errorMsg"
                        401, 403 -> "API Key अमान्य या अनधिकृत है। कृपया ऐप सेटिंग्स में सही Gemini API Key दर्ज करें।"
                        404 -> "मॉडल उपलब्ध नहीं है ($model)"
                        429 -> "API अनुरोध सीमा (Quota Limit) समाप्त हो गई है। कृपया कुछ क्षण बाद पुनः प्रयास करें।"
                        500, 503 -> "Google AI सर्वर वर्तमान में व्यस्त है। कृपया पुनः प्रयास करें।"
                        else -> "Gemini API त्रुटि (${response.code}): $errorMsg"
                    }

                    return GeminiResult.Error(friendlyMessage, response.code.toString())
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    Log.w(TAG, "API returned no candidates in response")
                    return GeminiResult.Error("मॉडल से कोई उत्तर प्राप्त नहीं हुआ।")
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
                    Log.d(TAG, "Successfully generated AI response (${resultText.length} chars)")
                    GeminiResult.Success(resultText)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network call failed: ${e.message}", e)
            GeminiResult.Error("नेटवर्क कनेक्शन त्रुटि: कृपया इंटरनेट कनेक्शन की जांच करें (${e.message})")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        return try {
            if (bitmap.isRecycled) return ""
            // Scale bitmap so max dimension is at most 1024 for fast transfer & optimal AI vision
            val maxDim = 1024
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDim || height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
                val newWidth = maxOf(1, (width * ratio).toInt())
                val newHeight = maxOf(1, (height * ratio).toInt())
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            // Compress as JPEG at 80% quality for optimal balance of image detail & fast transfer
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding bitmap to base64: ${e.message}", e)
            ""
        }
    }
}
