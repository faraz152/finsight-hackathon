package com.finsight

import com.finsight.pipeline.RegexExtractor
import kotlin.test.Test
import kotlin.test.assertEquals

class MerchantNameTest {
    @Test
    fun nameStopsAtConnectorWords() {
        assertEquals("SAJID committee", RegexExtractor.extract("Rs 15,000.00 sent to SAJID committee via Raast from A/C **4821. Ref: RA99120034").merchant)
        assertEquals("KFC Gulberg", RegexExtractor.extract("Rs 1,250.00 sent to KFC Gulberg via JazzCash. Fee: Rs 0.00. Balance: Rs 8,750.00. TID: 033050058799.").merchant)
    }
}
