package com.example.scanner

import java.text.DecimalFormat
import java.util.Locale
import java.util.regex.Pattern

data class ParsedBarcodeResult(
    val title: String,
    val displayPrice: String?,
    val numericPrice: Double?,
    val currency: String,
    val category: String,
    val details: String,
    val formatName: String,
    val keyValues: List<Pair<String, String>>
)

object BarcodePriceParser {

    private val priceFormatter = DecimalFormat("#,##0.00")

    // Database of known Sri Lankan & common retail barcodes (Soaps, grocery, supermarket items)
    private val knownProducts = mapOf(
        "8901030381001" to ProductInfo("Lifebuoy Total Soap Bar 100g", 140.0, "Rs.", "Soap & Body Care", "Unilever"),
        "8901030381018" to ProductInfo("Lifebuoy Care Soap 100g", 140.0, "Rs.", "Soap & Body Care", "Unilever"),
        "8901030381025" to ProductInfo("Lifebuoy Nature Soap 100g", 140.0, "Rs.", "Soap & Body Care", "Unilever"),
        "8901030010017" to ProductInfo("Lux Velvet Touch Soap 100g", 165.0, "Rs.", "Soap & Body Care", "Unilever"),
        "8901030010024" to ProductInfo("Lux Soft Touch Soap 100g", 165.0, "Rs.", "Soap & Body Care", "Unilever"),
        "4792022000018" to ProductInfo("Sunlight Lemon Soap Bar 110g", 120.0, "Rs.", "Soap & Laundry", "Unilever"),
        "4792022000025" to ProductInfo("Sunlight Sakura Soap Bar 110g", 120.0, "Rs.", "Soap & Laundry", "Unilever"),
        "5000108000012" to ProductInfo("Dettol Original Soap 100g", 175.0, "Rs.", "Antiseptic & Soap", "Reckitt"),
        "5000108000029" to ProductInfo("Dettol Cool Soap 100g", 175.0, "Rs.", "Antiseptic & Soap", "Reckitt"),
        "4792045001014" to ProductInfo("Baby Cheramy Floral Soap 100g", 135.0, "Rs.", "Baby Care & Soap", "Hemas"),
        "4792050002018" to ProductInfo("Velvet Rose & Milk Soap 100g", 150.0, "Rs.", "Soap & Body Care", "Hemas"),
        "4792055003011" to ProductInfo("Kohomba Herbal Soap 90g", 125.0, "Rs.", "Herbal Soap", "Swadeshi"),
        "4792055004018" to ProductInfo("Rani Sandalwood Soap 100g", 160.0, "Rs.", "Herbal Soap", "Swadeshi"),
        "4792035005012" to ProductInfo("Vim Dishwash Bar 200g", 110.0, "Rs.", "Kitchen & Cleaning", "Unilever"),
        "4792065006019" to ProductInfo("Rin Detergent Bar 125g", 95.0, "Rs.", "Laundry & Washing", "Unilever"),
        "4791001000014" to ProductInfo("Anchor Full Cream Milk Powder 400g", 1080.0, "Rs.", "Dairy & Beverages", "Fonterra"),
        "4792019001015" to ProductInfo("Munchee Super Cream Cracker 500g", 420.0, "Rs.", "Biscuits & Bakery", "Munchee"),
        "4792008002012" to ProductInfo("Maliban Gold Marie 300g", 280.0, "Rs.", "Biscuits & Bakery", "Maliban"),
        "4792027003017" to ProductInfo("Signal Herbal Toothpaste 120g", 240.0, "Rs.", "Oral Care", "Unilever"),
        "4792002004014" to ProductInfo("Clogard Fresh Toothpaste 120g", 220.0, "Rs.", "Oral Care", "Hemas"),
        "4791008005016" to ProductInfo("Elephant House Ginger Beer (EGB) 400ml", 180.0, "Rs.", "Beverages", "Elephant House"),
        "4792023006011" to ProductInfo("Astra Fat Spread 250g", 490.0, "Rs.", "Groceries", "Upfield"),
        "4792025007018" to ProductInfo("Sunlight Detergent Powder 1kg", 540.0, "Rs.", "Laundry & Washing", "Unilever"),
        "4792031008015" to ProductInfo("Harpic Power Plus 500ml", 380.0, "Rs.", "Home Cleaning", "Reckitt")
    )

    private data class ProductInfo(
        val name: String,
        val price: Double,
        val currency: String,
        val category: String,
        val brand: String
    )

    fun parse(rawValue: String, format: String): ParsedBarcodeResult {
        val trimmed = rawValue.trim()
        val fields = mutableListOf<Pair<String, String>>()
        fields.add("Barcode Format" to format)

        // 1. Check known product database (exact or normalized barcode)
        val cleanCode = trimmed.replace(Regex("[^0-9]"), "")
        if (cleanCode.isNotEmpty() && knownProducts.containsKey(cleanCode)) {
            val product = knownProducts[cleanCode]!!
            fields.add("Product Name" to product.name)
            fields.add("Brand" to product.brand)
            fields.add("Category" to product.category)
            fields.add("Barcode (EAN/UPC)" to cleanCode)
            val display = "${product.currency} ${priceFormatter.format(product.price)}"
            return ParsedBarcodeResult(
                title = product.name,
                displayPrice = display,
                numericPrice = product.price,
                currency = product.currency,
                category = product.category,
                details = "Brand: ${product.brand} | Standard Retail Price",
                formatName = format,
                keyValues = fields
            )
        }

        // 2. Check EMVCo / LankaQR / UPI QR format (standard on bills, invoices, receipts)
        val emvcoResult = tryParseEmvcoOrLankaQr(trimmed, format, fields)
        if (emvcoResult != null) {
            return emvcoResult
        }

        // 3. Check JSON structure (often used on POS / supermarket receipt QRs)
        val jsonResult = tryParseJsonBill(trimmed, format, fields)
        if (jsonResult != null) {
            return jsonResult
        }

        // 4. Check URL query parameters or text payload for price indicators
        val (price, numericVal, currency) = extractPriceFromText(trimmed)

        // Determine title & category based on content & format
        val isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        val originCountry = getCountryFromBarcode(cleanCode)
        if (originCountry != null) {
            fields.add("Country of Origin" to originCountry)
        }

        val title = when {
            price != null && (trimmed.contains("bill", true) || trimmed.contains("inv", true) || trimmed.contains("receipt", true)) -> {
                "Store Bill / Receipt"
            }
            price != null && isBarcodeFormat(format) -> {
                "Scanned Product Barcode"
            }
            price != null -> {
                "Scanned Bill / Price QR"
            }
            isBarcodeFormat(format) -> {
                "Product Barcode (${cleanCode.take(13)})"
            }
            isUrl -> {
                "Web Link / Invoice URL"
            }
            else -> {
                "Scanned QR Code"
            }
        }

        val category = when {
            trimmed.contains("bill", true) || trimmed.contains("inv", true) || trimmed.contains("total", true) -> "Bill & Receipt"
            isBarcodeFormat(format) -> "Product / Grocery"
            isUrl -> "Web & Online"
            else -> "General"
        }

        // Extract invoice/bill numbers, date, merchant if found in text
        extractMetadataFromText(trimmed, fields)

        fields.add("Raw Content" to if (trimmed.length > 150) trimmed.take(150) + "..." else trimmed)

        return ParsedBarcodeResult(
            title = title,
            displayPrice = price,
            numericPrice = numericVal,
            currency = currency,
            category = category,
            details = if (price != null) "Extracted price: $price" else "Code: $trimmed",
            formatName = format,
            keyValues = fields
        )
    }

    /**
     * Parse LankaQR / UPI / EMVCo Merchant-Presented QR codes
     */
    private fun tryParseEmvcoOrLankaQr(
        data: String,
        format: String,
        fields: MutableList<Pair<String, String>>
    ): ParsedBarcodeResult? {
        // EMVCo starts with "000201"
        if (!data.startsWith("000201")) return null

        var index = 0
        var amount: Double? = null
        var merchantName: String? = null
        var merchantCity: String? = null
        var invoiceRef: String? = null
        var currencyStr = "Rs."

        try {
            while (index + 4 <= data.length) {
                val tag = data.substring(index, index + 2)
                val lenStr = data.substring(index + 2, index + 4)
                val len = lenStr.toIntOrNull() ?: break
                val startVal = index + 4
                val endVal = startVal + len
                if (endVal > data.length) break
                val value = data.substring(startVal, endVal)
                index = endVal

                when (tag) {
                    "54" -> {
                        amount = value.toDoubleOrNull()
                    }
                    "53" -> {
                        currencyStr = if (value == "144") "Rs." else if (value == "840") "$" else "Rs."
                    }
                    "59" -> {
                        merchantName = value
                    }
                    "60" -> {
                        merchantCity = value
                    }
                    "62" -> {
                        // Additional data template, extract invoice or bill reference
                        invoiceRef = value
                    }
                }
            }

            merchantName?.let { fields.add("Merchant" to it) }
            merchantCity?.let { fields.add("City / Location" to it) }
            invoiceRef?.let { fields.add("Reference / Bill #" to it) }
            fields.add("Payment Standard" to "LankaQR / EMVCo Standard")

            val display = amount?.let { "$currencyStr ${priceFormatter.format(it)}" }
            val titleName = merchantName?.let { "$it Bill" } ?: "LankaQR Bill Payment"

            return ParsedBarcodeResult(
                title = titleName,
                displayPrice = display,
                numericPrice = amount,
                currency = currencyStr,
                category = "Supermarket & Store Bill",
                details = "LankaQR / Merchant Payment Bill",
                formatName = format,
                keyValues = fields
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Checks if the payload is JSON-like with price fields
     */
    private fun tryParseJsonBill(
        data: String,
        format: String,
        fields: MutableList<Pair<String, String>>
    ): ParsedBarcodeResult? {
        if (!data.startsWith("{") || !data.endsWith("}")) return null

        val amountRegex = Pattern.compile(""""(?:amount|total|price|gross|net|amt)"\s*:\s*"?([0-9.,]+)"?""", Pattern.CASE_INSENSITIVE)
        val matcher = amountRegex.matcher(data)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "") ?: return null
            val value = numStr.toDoubleOrNull() ?: return null
            val currency = if (data.contains("USD") || data.contains("$")) "$" else "Rs."
            val display = "$currency ${priceFormatter.format(value)}"

            fields.add("Bill Total" to display)
            val merchantRegex = Pattern.compile(""""(?:merchant|store|shop|name|vendor)"\s*:\s*"([^"]+)"""", Pattern.CASE_INSENSITIVE)
            val mMatch = merchantRegex.matcher(data)
            val merchant = if (mMatch.find()) mMatch.group(1) else null
            merchant?.let { fields.add("Merchant" to it) }

            val invRegex = Pattern.compile(""""(?:invoice|bill|receipt|ref)"\s*:\s*"([^"]+)"""", Pattern.CASE_INSENSITIVE)
            val invMatch = invRegex.matcher(data)
            val invNo = if (invMatch.find()) invMatch.group(1) else null
            invNo?.let { fields.add("Invoice #" to it) }

            return ParsedBarcodeResult(
                title = merchant?.let { "$it Receipt" } ?: "Store Receipt Bill",
                displayPrice = display,
                numericPrice = value,
                currency = currency,
                category = "Digital Receipt",
                details = "JSON Receipt Data",
                formatName = format,
                keyValues = fields
            )
        }
        return null
    }

    /**
     * Extracts price indicators and amounts from any text or bill data
     */
    fun extractPriceFromText(text: String): Triple<String?, Double?, String> {
        // Pattern 1: Total / Price / Amount labels followed by numbers (e.g. Total: 1,450.00, Price: Rs 450)
        val labeledPatterns = listOf(
            Pattern.compile("""(?i)(?:total|amount|price|net|amt|gross|bill|මිල|ගාණ|මුදල)[:\s=]+(?:(?:Rs\.?|LKR|\$|USD|EUR|€|INR|₹)?\s*)?([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""),
            Pattern.compile("""(?i)(?:(?:Rs\.?|LKR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?))"""),
            Pattern.compile("""(?i)(?:\$\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?))"""),
            Pattern.compile("""([0-9]+(?:\.[0-9]{2})?)\s*(?:/=|LKR|Rs\.?)""")
        )

        for (pattern in labeledPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val rawNumber = matcher.group(1)?.replace(",", "")
                val numericVal = rawNumber?.toDoubleOrNull()
                if (numericVal != null && numericVal > 0) {
                    val curr = if (text.contains("$") || text.contains("USD")) "$" else "Rs."
                    return Triple("$curr ${priceFormatter.format(numericVal)}", numericVal, curr)
                }
            }
        }

        // Pattern 2: URL query param (e.g. ?amount=450 or ?amt=1200.50 or ?price=350)
        val urlParamPattern = Pattern.compile("""[?&](?:amount|amt|price|total|val)=([0-9]+(?:\.[0-9]{1,2})?)""")
        val urlMatcher = urlParamPattern.matcher(text)
        if (urlMatcher.find()) {
            val numVal = urlMatcher.group(1)?.toDoubleOrNull()
            if (numVal != null && numVal > 0) {
                return Triple("Rs. ${priceFormatter.format(numVal)}", numVal, "Rs.")
            }
        }

        return Triple(null, null, "Rs.")
    }

    private fun extractMetadataFromText(text: String, fields: MutableList<Pair<String, String>>) {
        // Look for Invoice or Bill reference
        val invPattern = Pattern.compile("""(?i)(?:inv(?:oice)?|bill|receipt|ref)[:\s#-]+([A-Z0-9_-]{4,20})""")
        val invMatcher = invPattern.matcher(text)
        if (invMatcher.find()) {
            invMatcher.group(1)?.let { fields.add("Invoice / Bill #" to it) }
        }

        // Look for Date
        val datePattern = Pattern.compile("""\b(202[4-9][-/.](?:0[1-9]|1[0-2])[-/.](?:0[1-9]|[12][0-9]|3[01]))\b""")
        val dateMatcher = datePattern.matcher(text)
        if (dateMatcher.find()) {
            dateMatcher.group(1)?.let { fields.add("Date" to it) }
        }
    }

    private fun isBarcodeFormat(format: String): Boolean {
        return format.contains("EAN") || format.contains("UPC") || format.contains("CODE_128") || format.contains("CODE_39")
    }

    private fun getCountryFromBarcode(code: String): String? {
        if (code.length < 3) return null
        val prefix = code.take(3).toIntOrNull() ?: return null
        return when (prefix) {
            in 479..479 -> "Sri Lanka (479)"
            in 890..890 -> "India (890)"
            in 0..19, in 30..39, in 60..139 -> "United States & Canada"
            in 400..440 -> "Germany"
            in 450..459, in 490..499 -> "Japan"
            in 500..509 -> "United Kingdom"
            in 690..699 -> "China"
            in 880..880 -> "South Korea"
            in 885..885 -> "Thailand"
            in 888..888 -> "Singapore"
            in 893..893 -> "Vietnam"
            in 930..939 -> "Australia"
            else -> null
        }
    }
}
