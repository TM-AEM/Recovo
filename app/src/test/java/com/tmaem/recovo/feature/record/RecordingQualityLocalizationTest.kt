package com.tmaem.recovo.feature.record

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tmaem.recovo.R
import com.tmaem.recovo.core.engine.RecordingQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that the recording-quality presentation layer resolves from string resources
 * under both the default (English) and Arabic locales, and that the domain enum's
 * technical fields are untouched by localization.
 *
 * The domain enum itself carries no Android/Compose dependency; localization lives in the
 * presentation mapping inside RecordingQualitySelector.kt, so these tests assert resource
 * resolution directly against the app's resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordingQualityLocalizationTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

  @Test
  fun qualityHeading_resolvesFromResources() {
    val heading = str(R.string.quality_card_heading)
    assertTrue("heading must not be blank", heading.isNotBlank())
    assertEquals("Recording Quality Preset", heading)
  }

  @Test
  fun everyPreset_hasNonEmptyLocalizedTitlesSubtitlesDescriptions() {
    val ids = listOf(
      Triple(R.string.quality_standard_short, R.string.quality_standard_title, R.string.quality_standard_subtitle),
      Triple(R.string.quality_high_short, R.string.quality_high_title, R.string.quality_high_subtitle),
      Triple(R.string.quality_maximum_short, R.string.quality_maximum_title, R.string.quality_maximum_subtitle),
    )
    val descriptions = listOf(
      R.string.quality_standard_description,
      R.string.quality_high_description,
      R.string.quality_maximum_description,
    )
    ids.forEach { (short, title, subtitle) ->
      assertTrue(str(short).isNotBlank())
      assertTrue(str(title).isNotBlank())
      assertTrue(str(subtitle).isNotBlank())
    }
    descriptions.forEach { assertTrue(str(it).isNotBlank()) }
  }

  @Test
  fun englishResources_matchExpectedLabels() {
    assertEquals("Standard", str(R.string.quality_standard_short))
    assertEquals("High Quality", str(R.string.quality_high_title))
    assertEquals("Maximum Quality", str(R.string.quality_maximum_title))
  }

  @Test
  @Config(qualifiers = "ar")
  fun arabicResources_resolveNaturally_notEnglish() {
    val title = str(R.string.quality_high_title)
    assertEquals("جودة عالية", title)
    assertNotEquals("High Quality", title)
    assertTrue(str(R.string.quality_card_heading).isNotBlank())
    assertTrue(str(R.string.quality_standard_description).isNotBlank())
  }

  @Test
  @Config(qualifiers = "ar")
  fun arabicAndEnglish_differForTitles() {
    val ar = str(R.string.quality_maximum_title)
    assertNotEquals("Maximum Quality", ar)
  }

  @Test
  fun domainEnum_technicalFieldsUnchangedByLocalization() {
    // Localization must not touch bitrates / sample rates / channel counts.
    assertEquals(96000, RecordingQuality.STANDARD.bitRate)
    assertEquals(192000, RecordingQuality.HIGH.bitRate)
    assertEquals(256000, RecordingQuality.MAXIMUM.bitRate)
    assertEquals(44100, RecordingQuality.HIGH.sampleRate)
    assertEquals(1, RecordingQuality.MAXIMUM.channelCount)
    // The English source strings preserve the historical technical subtitle text.
    assertEquals("96 kbps • 44.1 kHz • Mono", str(R.string.quality_standard_subtitle))
    assertEquals("96 kbps", str(R.string.quality_bitrate_kbps, 96))
  }

  @Test
  @Config(qualifiers = "ar")
  fun arabicBitrateUnit_isLocalized() {
    // %d formats with the locale's digits in Arabic, hence Arabic-Indic numerals.
    assertEquals("٩٦ كيلوبت/ث", str(R.string.quality_bitrate_kbps, 96))
  }
}
