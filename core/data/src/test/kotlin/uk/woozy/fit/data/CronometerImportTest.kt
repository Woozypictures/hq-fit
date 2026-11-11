package uk.woozy.fit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.woozy.fit.data.db.Diary
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.imports.CronometerImport
import uk.woozy.fit.model.Nutrients

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CronometerImportTest {
    private lateinit var db: HqFitDatabase

    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HqFitDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()

    // Same header names as a real Cronometer daily summary, cut down to a few columns.
    private val csv = "﻿Date,Energy (kcal),Vitamin D (IU),Net Carbs (g),Carbs (g),Fiber (g),Fat (g),Protein (g),Water (g),Completed\n" +
        "2023-05-16,,,,,,,,,false\n" +
        "2023-05-18,2335.12,2800.00,309.31,364.60,6.89,52.37,147.13,960.98,false\n" +
        "2023-05-19,1800,0,100,120,20,40,90,500,true\n"

    @Test
    fun parsesLoggedDaysAndConvertsUnits() {
        val days = CronometerImport.parseDaily(csv)
        assertEquals(listOf("2023-05-18", "2023-05-19"), days.map { it.date })
        val d = days.first().nutrients
        assertEquals(2335.12, d["energy"]!!, 0.001)
        assertEquals(70.0, d["vit_d"]!!, 0.001)       // 2800 IU
        assertEquals(364.60, d["carbs"]!!, 0.001)
        assertTrue("water is food water, not drinks, so it is not imported", d["water"] == null)
    }

    @Test
    fun importAddsEachDayOnceAndNetCarbsComeFromCarbsMinusFibre() = runBlocking {
        assertEquals(2, CronometerImport.import(db, csv, 1L))
        assertEquals(0, CronometerImport.import(db, csv, 2L))
        val t = Diary.totals(db.diary().day("2023-05-18"))
        assertEquals(364.60 - 6.89, Nutrients.netCarbs(t), 0.001)
    }

    @Test
    fun quotedFieldsAndWrongFilesAreHandled() {
        assertEquals(listOf("a,b", "c\"d", "e"), CronometerImport.splitCsv("\"a,b\",\"c\"\"d\",e"))
        try { CronometerImport.parseDaily("Day,Group,Food Name\n1,2,3"); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { }
    }
}
