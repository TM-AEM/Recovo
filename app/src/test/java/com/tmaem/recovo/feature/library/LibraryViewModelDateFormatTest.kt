package com.tmaem.recovo.feature.library

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone

/**
 * Verifies that [LibraryViewModel.formatDate] derives its presentation pattern from the
 * current locale (via DateFormat.getBestDateTimePattern) rather than using a fixed pattern.
 * The instant is pinned to 2023-01-01T00:10:00Z and the timezone to UTC so only the
 * locale-driven pattern/column ordering varies. Expected values were captured by observing
 * the implementation on this toolchain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryViewModelDateFormatTest {

  private val instant = 1_672_531_800_000L // 2023-01-01T00:10:00Z
  private lateinit var savedLocale: Locale
  private lateinit var savedTimeZone: TimeZone

  @Before
  fun pinDefaults() {
    savedLocale = Locale.getDefault()
    savedTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  }

  @After
  fun restoreDefaults() {
    Locale.setDefault(savedLocale)
    TimeZone.setDefault(savedTimeZone)
  }

  @Test
  fun `us locale yields month-first 12-hour pattern`() {
    Locale.setDefault(Locale.US)
    assertEquals("Jan 1, 2023, 12:10 AM", LibraryViewModel.formatDate(instant))
  }

  @Test
  fun `arabic locale yields day-first localized pattern with arabic-indic digits`() {
    Locale.setDefault(Locale("ar"))
    assertEquals("\u0661 \u064A\u0646\u0627\u064A\u0631 \u0662\u0660\u0662\u0663\u060C \u0661\u0662:\u0661\u0660 \u0635", LibraryViewModel.formatDate(instant))
  }

  @Test
  fun `british locale yields day-first pattern`() {
    Locale.setDefault(Locale.UK)
    assertEquals("1 Jan 2023, 12:10\u202Fam", LibraryViewModel.formatDate(instant))
  }

  @Test
  fun `french locale localizes month name`() {
    Locale.setDefault(Locale.FRANCE)
    assertEquals("1 janv. 2023, 12:10\u202FAM", LibraryViewModel.formatDate(instant))
  }

  @Test
  fun `output differs across locales for the same instant`() {
    Locale.setDefault(Locale.US)
    val us = LibraryViewModel.formatDate(instant)
    Locale.setDefault(Locale("ar"))
    val ar = LibraryViewModel.formatDate(instant)
    Locale.setDefault(Locale.UK)
    val uk = LibraryViewModel.formatDate(instant)
    assertNotEquals(us, ar)
    assertNotEquals(us, uk)
    assertNotEquals(ar, uk)
  }

  @Test
  fun `arabic output uses right-to-left separators and arabic-indic numerals`() {
    Locale.setDefault(Locale("ar"))
    val out = LibraryViewModel.formatDate(instant)
    assertTrue("expected arabic-indic digits in $out", out.contains("\u0661"))
    assertTrue("expected arabic comma in $out", out.contains("\u060C"))
  }
}
