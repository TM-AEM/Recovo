package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecovoThemeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun capture(darkTheme: Boolean): ColorScheme {
    lateinit var scheme: ColorScheme
    composeTestRule.setContent {
      RecovoTheme(darkTheme = darkTheme) {
        scheme = MaterialTheme.colorScheme
      }
    }
    composeTestRule.waitForIdle()
    return scheme
  }

  @Test
  fun darkScheme_usesAmberBrand_primaryAndSecondaryContainer() {
    val scheme = capture(darkTheme = true)
    assertEquals(AmberPrimaryDark, scheme.primary)
    assertEquals(AmberOnPrimaryDark, scheme.onPrimary)
    assertEquals(BronzeSecondaryContainerDark, scheme.secondaryContainer)
    assertEquals(OnSecondaryContainerDark, scheme.onSecondaryContainer)
  }

  @Test
  fun lightScheme_usesAmberBrand_primaryAndSecondaryContainer() {
    val scheme = capture(darkTheme = false)
    assertEquals(AmberPrimaryLight, scheme.primary)
    assertEquals(BronzeSecondaryContainerLight, scheme.secondaryContainer)
  }

  @Test
  fun errorRole_staysRed_forRecordingAndDestructiveActions() {
    val scheme = capture(darkTheme = true)
    assertEquals(RedErrorDark, scheme.error)
    // Red channel dominant => not recolored to amber.
    assertTrue(scheme.error.red > scheme.error.green)
    assertTrue(scheme.error.red > scheme.error.blue)
  }

  @Test
  fun surfaceVariant_isDefined_notDefaultPurpleFallback() {
    val scheme = capture(darkTheme = true)
    assertEquals(CharcoalSurfaceVariantDark, scheme.surfaceVariant)
    // Guard against reverting to the Material baseline purple-grey.
    assertNotEquals(Color(0xFF4A4458), scheme.surfaceVariant)
  }
}
