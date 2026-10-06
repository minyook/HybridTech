package com.minyook.sllm2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcePdfCatalogTest {
    @Test
    fun sourcePageNumbersMapToTheSamePhysicalPdfPages() {
        val guide = SourcePdfCatalog.find("confined_space_safety_guide_2022")!!
        val checklist = SourcePdfCatalog.find("confined_space_checklist_2026")!!

        assertEquals(84, guide.pageCount)
        assertEquals(44, guide.pageIndex(45))
        assertEquals(26, guide.pageIndex(27))
        assertEquals(1, checklist.pageIndex(2))
        assertNull(guide.pageIndex(85))
        assertNull(checklist.pageIndex(0))
    }
}
