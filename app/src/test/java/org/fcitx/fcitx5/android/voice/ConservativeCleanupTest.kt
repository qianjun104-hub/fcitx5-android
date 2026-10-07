package org.fcitx.fcitx5.android.voice

import org.fcitx.fcitx5.android.input.voice.ConservativeCleanup
import org.junit.Assert.*
import org.junit.Test

class ConservativeCleanupTest {
    private val glossary = "头孢唑林，股骨颈，CT，MRI，PCT，CRP，PVP，TFCC，左氧氟沙星"

    @Test fun punctuationAndFillerRemovalKeepClinicalFacts() {
        val original = "嗯患者右侧股骨颈术后体温39.5℃CRP20.9头孢唑林1g每8h一次未见左侧疼痛"
        val p = ConservativeCleanup.protect(original, glossary)
        val response = p.masked.removePrefix("嗯").replace("术后", "术后，") + "。"
        val cleaned = ConservativeCleanup.restoreIfSafe(p, response)
        assertNotNull(cleaned)
        assertTrue(cleaned!!.contains("39.5℃"))
        assertTrue(cleaned.contains("未见左侧疼痛"))
        assertTrue(cleaned.contains("头孢唑林1g每8h一次"))
    }

    @Test fun glossarySurvivesEvenWhenItContainsSideCharacters() {
        val p = ConservativeCleanup.protect("左氧氟沙星500mg没有过敏", glossary)
        assertEquals("左氧氟沙星500mg没有过敏。", ConservativeCleanup.restoreIfSafe(p, p.masked + "。"))
    }

    @Test fun missingDuplicatedReorderedOrChangedProtectedFactsAreRejected() {
        val p = ConservativeCleanup.protect("右侧疼痛钾3.5CT阴性", glossary)
        val first = p.tokens.first().first
        val last = p.tokens.last().first
        val cases = listOf(
            p.masked.replace(first, ""), p.masked + first,
            p.masked.replace(first, "SWAP").replace(last, first).replace("SWAP", last),
            p.masked.replace(first, "左侧"), p.masked.replace(first, first.replace("_0", "_999"))
        )
        for (response in cases) assertNull(response, ConservativeCleanup.restoreIfSafe(p, response))
    }

    @Test fun chineseAdjacentAbbreviationsAndDoseExpressionsAreProtected() {
        val p = ConservativeCleanup.protect("复查PCT和CRP钾≤3.5剂量0.25mg血压108/52MRI未见异常第九床", glossary)
        val facts = p.tokens.map { it.second.trim() }
        for (expected in listOf("PCT", "CRP", "≤3.5", "0.25", "mg", "108/52", "MRI", "未见", "九"))
            assertTrue("missing $expected in $facts", expected in facts)
        assertEquals(p.source, ConservativeCleanup.restoreIfSafe(p, p.masked))
    }

    @Test fun newAnswersParaphrasesAndHallucinationsAreRejected() {
        val p = ConservativeCleanup.protect("这个患者怎么办我需要请示上级", glossary)
        for (response in listOf("建议立即请心内科会诊", p.masked + "应该立即用药", "请示上级"))
            assertNull(ConservativeCleanup.restoreIfSafe(p, response))
    }

    @Test fun clauseReorderingAndBlankOutputAreRejected() {
        val p = ConservativeCleanup.protect("先复查血常规然后再给上级汇报", glossary)
        assertNull(ConservativeCleanup.restoreIfSafe(p, "然后再给上级汇报先复查血常规"))
        assertNull(ConservativeCleanup.restoreIfSafe(p, ""))
    }

    @Test fun duplicateAndRegexSpecialGlossaryEntriesCannotBreakMasking() {
        val p = ConservativeCleanup.protect("TFCC和C++都保留", "TFCC，TFCC，C++")
        assertEquals(p.source, ConservativeCleanup.restoreIfSafe(p, p.masked))
    }

    @Test fun omissionOfUnlistedSymptomsAndPlansIsRejected() {
        for ((original, shortened) in listOf(
            "患者发热需要复查血常规" to "患者需要复查血常规",
            "患者需要复查血常规并且请示上级" to "患者需要复查血常规",
            "先复查血常规然后给上级汇报" to "先复查血常规给上级汇报",
            "患者额部有伤口需要换药" to "患者部有伤口需要换药"
        )) {
            val p = ConservativeCleanup.protect(original, "")
            assertNull(original, ConservativeCleanup.restoreIfSafe(p, shortened))
        }
    }

    @Test fun adjacentWholePhraseRepetitionCanBeRemoved() {
        val p = ConservativeCleanup.protect("嗯患者需要患者需要复查血常规", "")
        assertEquals("患者需要复查血常规。", ConservativeCleanup.restoreIfSafe(p, "患者需要复查血常规。"))
        val single = ConservativeCleanup.protect("患者咳咳血需要复查", "")
        assertNull(ConservativeCleanup.restoreIfSafe(single, "患者咳血需要复查"))
    }

    @Test fun temperaturePercentAndLabResultSymbolsCannotDisappear() {
        val p = ConservativeCleanup.protect("体温39.5℃氧饱和度95%尿蛋白++", "")
        assertEquals(p.source + "。", ConservativeCleanup.restoreIfSafe(p, p.masked + "。"))
        for (symbol in listOf("℃", "%", "++")) {
            val marker = p.tokens.first { it.second == symbol }.first
            assertNull(symbol, ConservativeCleanup.restoreIfSafe(p, p.masked.replace(marker, "")))
        }
    }
}
