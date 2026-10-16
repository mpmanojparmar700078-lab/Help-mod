package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun speechNormalizer_cleansMarkdownAndFormatsHinglish() {
    val input = "**Step 1**: Wi-Fi settings open karein aur `Connect` button par tap karein! https://example.com"
    val normalized = com.example.voice.SpeechNormalizer.normalizeForSpeech(input)

    assertFalse(normalized.contains("**"))
    assertFalse(normalized.contains("`"))
    assertTrue(normalized.contains("वाइफ़ाई"))
    assertTrue(normalized.contains("सेटिंग्स"))
    assertTrue(normalized.contains("ओपन"))
    assertTrue(normalized.contains("बटन"))
    assertTrue(normalized.contains("टैप"))
    assertTrue(normalized.contains("वेबसाइट लिंक"))
  }
}
