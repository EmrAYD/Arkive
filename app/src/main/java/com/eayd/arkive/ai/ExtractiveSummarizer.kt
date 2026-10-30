package com.eayd.arkive.ai

import java.util.Locale
import kotlin.math.pow

/**
 * Telefonda çalışan özetleyici: yapay zeka modeli kullanmaz, belgenin kendi cümlelerini puanlayıp en önemlilerini seçer
 * ("çıkarımsal özetleme"). Hiçbir Android sınıfı kullanmaz; tek başına test edilebilir (bkz. ExtractiveSummarizerTest).
 *
 * Yöntem:
 *  1. Metin temizlenir: sayfa altbilgileri ("Sayfa 3 / 6", her sayfada tekrar eden satırlar), bağlantı ve kullanıcı adı
 *     satırları atılır; PDF'in "M ekanik" gibi böldüğü büyük harfler birleştirilir.
 *  2. Metin başlıklara (kısa, BÜYÜK HARFLİ ya da noktalamasız satırlar), paragraf ve cümlelere bölünür (kısaltmalar,
 *     madde işaretleri ve PDF'in satır sonları dikkate alınır). "NOT:" / "DİKKAT:" ile başlayan cümleler vurgulanmış
 *     bilgidir; etiketi ayrı tutulur.
 *  3. Her cümlenin anlamlı kelimeleri çıkarılır: Türkçe bağlaç/zamir gibi sık kelimeler atılır, kelimeler ilk 5 harfe
 *     indirilerek ekleri farklı biçimler ("depolama", "depolamak") birleştirilir.
 *  4. Kelime ağırlığı, kelimenin geçtiği cümle sayısına (df) göre verilir: df x (1 - df / (cümle sayısı + 1)). Tek bir
 *     cümlede geçen kelime (tesadüf) da, hemen her cümlede geçen genel kelime de az; belgenin konusunu taşıyan, orta
 *     sıklıktaki kelimeler çok puan alır. Cümle puanı kelimelerinin ağırlık toplamıdır; uzunluğa göre yumuşatılır,
 *     belgenin ve paragrafların ilk cümlesi, tanım cümleleri ("... denir") ve vurgulu notlar küçük bir ek puan alır;
 *     "Böylece", "Ayrıca" gibi önceki cümleye bağlı başlayanlar puan kaybeder (tek başına anlaşılmazlar).
 *  5. Belgede başlıklar varsa kontenjan konulara dağıtılır: her konudan en az bir cümle seçilir (uzun konular daha
 *     fazla), yoksa tüm belgeden en yüksek puanlılar seçilir. Çok benzeyen cümle atlanır. Seçilenler belgedeki sırasıyla
 *     dizilir: ilki "Özet", kalanları konu başlıkları altında (başlık yoksa "Önemli noktalar") madde olur.
 */
object ExtractiveSummarizer {

    /** Sunucuya kaydedilirken yazılan yöntem adı (yalnızca bilgi amaçlı). */
    const val NAME = "cümle puanlama (telefonda)"

    /** Bir cümle özete aday olmak için en az bu kadar kelime içermelidir (başlık ve kısa satırlar elenir). */
    private const val MIN_WORDS = 5

    /** Bu cümle sayısına kadar her 5 cümlenin biri özete girer (30 cümlede 6 madde); üst sınır yoktur. */
    private const val DENSE_UNTIL = 30

    /** [DENSE_UNTIL]'den sonra her bu kadar cümle bir madde daha getirir: uzun belgede özet de uzar ama belge kadar şişmez. */
    private const val SENTENCES_PER_EXTRA_POINT = 25

    /** Başlıklı belgede en az bu kadar konu varsa özet konu başlıkları altında verilir. */
    private const val MIN_SECTIONS = 3

    /** Konular ortalama en az bu kadar cümle içermiyorsa "başlıklar" gürültüdür (kısa satırlar); özet başlıksız yapılır. */
    private const val MIN_SENTENCES_PER_SECTION = 3

    /** Başlıklı belgede madde sayısı konu sayısının bu katından az olamaz (her konu temsil edilsin) ama normal sayının 2 katını aşmaz. */
    private const val POINTS_PER_SECTION = 2
    private const val MAX_POINT_SHARE = 2

    /** Konunun ilk cümlesi çoğunlukla konuyu tanımlar; konu başlığındaki kavramı anan cümle (en çok 2 kavram) asıl cümledir. */
    private const val FIRST_IN_SECTION_BOOST = 1.15
    private const val TITLE_MATCH_BOOST = 0.2

    /** Konu içinde seçimde konu içi puanın payı (kalanı belge geneli puan). */
    private const val SECTION_SCORE_SHARE = 0.6

    /** Bir konudan en çok bu kadar madde seçilir. */
    private const val MAX_POINTS_PER_SECTION = 4

    /** Özetteki bir cümlenin en çok karakteri; fazlası kelime sınırından kesilip "…" eklenir. */
    private const val MAX_SENTENCE_CHARS = 300

    /** İki cümlenin kelime kümeleri bu orandan fazla örtüşüyorsa ikincisi atlanır. */
    private const val SIMILARITY_LIMIT = 0.5

    /** Kelimeler bu kadar harfe indirilir (Türkçe ekleri kabaca atmak için). */
    private const val STEM_LENGTH = 5

    /** PDF/metin satırı bu kadar uzunsa ve noktalamayla bitmiyorsa sonraki satır aynı cümlenin devamı sayılır. */
    private const val WRAPPED_LINE_MIN = 50

    /** Başlık sayılacak satırın en çok kelime ve karakter sayısı. */
    private const val HEADING_MAX_WORDS = 7
    private const val HEADING_MAX_CHARS = 60

    /** Bir satır belgede bu kadar ya da daha fazla kez aynen (sayılar hariç) tekrar ediyorsa üst/alt bilgidir. */
    private const val REPEATED_LINE_MIN = 3

    private val turkish: Locale = Locale.forLanguageTag("tr-TR")
    private val wordRegex = Regex("[\\p{L}\\p{N}]+")
    private val suffixAfterApostrophe = Regex("['’]\\p{L}*")
    private val listMarker = Regex("^(?:[-•*▪●○·\\uE000-\\uF8FF]+\\s*|\\d{1,2}[.)]\\s+)")
    private val pageNumberLine = Regex("(?i)^(?:.*\\bsayfa\\s*)?\\d{1,4}\\s*(?:/|of)\\s*\\d{1,4}$|^sayfa\\s*\\d{1,4}$|^[-–]\\s*\\d{1,4}\\s*[-–]$")
    private val numberedHeading = Regex("^\\d{1,2}(?:\\.\\d{1,2})*[.)]?\\s+\\p{Lu}[^.!?;,:]*$")
    private val noiseLine = Regex("(?i)(?:\\s|\\()@\\w|https?://|www\\.|\\S+@\\S+\\.\\w")
    private val calloutLabel = Regex("^(NOT|DİKKAT|ÖNEMLİ|UYARI|İPUCU|ÖRNEK)\\s*[:：]\\s*", RegexOption.IGNORE_CASE)
    private val definition = Regex("(?i)\\b(?:denir|denilir|denmektedir|adı verilir|adlandırılır|olarak bilinir|olarak tanımlanır|tanımlanır|denilen|adı verilen|adlandırılan)\\b")
    private val dependentStart = Regex("^(?:ayrıca|böylece|dolayısıyla|buna ek olarak|bunun sonucunda|bu nedenle|bu yüzden|ancak|fakat|çünkü|ve|ama|yani|bu|bunlar|bunun|bunu|bunlara|o|onlar)\\b", RegexOption.IGNORE_CASE)

    /** PDF'in bazı yazı tiplerinde büyük harften sonra koyduğu boşluk ("M ekanik"). Anlamlı tek harfler (A, B, K vitamini, O) hariç. */
    private val splitCapital = Regex("(?<![\\p{L}\\p{N}])([\\p{Lu}&&[^ABCDEKO]]) (\\p{Ll}{3,})")

    private class Sentence(
        val index: Int,
        val text: String,
        val stems: Set<String>,
        val paragraphStart: Boolean,
        val words: Int,
        val section: Int,
        /** "NOT:" gibi vurgu etiketi (ilk harfi büyük, ":" olmadan) ya da null. */
        val label: String?,
    )

    private class Parsed(val sentences: List<Sentence>, val titles: List<String>) {
        /** Konu başlığının kelime kökleri (konu numarasına göre); cümle başlıktaki kavramı anıyorsa o konunun asıl cümlesidir. */
        val titleStems: List<Set<String>> = titles.map { stemsOf(it) }
    }

    /** Özeti döndürür; metinde özetlenecek cümle yoksa boş metin. */
    fun summarize(text: String): String {
        val parsed = parse(clean(text))
        val sentences = parsed.sentences
        if (sentences.isEmpty()) return ""
        // Çok kısa belge: seçmeye gerek yok, olduğu gibi gösterilir.
        if (sentences.size <= 3) return "Özet: " + sentences.joinToString(" ") { clip(withLabel(it)) }

        val n = sentences.size
        val weights = termWeights(sentences)
        val bySection = sentences.groupBy { it.section }
        val sectioned = bySection.size >= MIN_SECTIONS && n >= bySection.size * MIN_SENTENCES_PER_SECTION
        val sectionWeights = if (sectioned) sectionTermWeights(bySection) else emptyMap()
        val firstOfSection = bySection.mapValues { it.value.first() }

        fun boosted(sentence: Sentence, base: Double): Double {
            var v = base * multiplier(sentence)
            if (sectioned) {
                if (firstOfSection[sentence.section] === sentence && sentence.section != 0) v *= FIRST_IN_SECTION_BOOST
                val overlap = sentence.stems.count { it in parsed.titleStems[sentence.section] }
                v *= 1.0 + TITLE_MATCH_BOOST * minOf(overlap, 2)
            }
            return v
        }
        // Belge genelindeki önem (özet cümlesi ve başlıksız belge) ve konu içindeki önem (başlıklı belgede madde seçimi).
        val scored = sentences.associateWith { boosted(it, baseScore(it, weights)) }
        val inSection = if (!sectioned) emptyMap() else bySection.values.flatMap { list ->
            // Konu içi ve belge geneli puan, o konudaki en yükseğe göre ölçeklenip karıştırılır: küçük konularda (tüm kelimeler
            // bir kez geçer) konu içi puan ayırt edemez, belge geneli puan da genel kelimeleri öne çıkarır.
            val local = list.map { baseScore(it, sectionWeights.getValue(it.section)) }
            val global = list.map { baseScore(it, weights) }
            val maxLocal = local.max().takeIf { it > 0 } ?: 1.0
            val maxGlobal = global.max().takeIf { it > 0 } ?: 1.0
            list.mapIndexed { i, sentence ->
                sentence to boosted(sentence, SECTION_SCORE_SHARE * local[i] / maxLocal + (1 - SECTION_SCORE_SHARE) * global[i] / maxGlobal)
            }
        }.toMap()
        val pointCount = (if (n <= DENSE_UNTIL) n / 5 else DENSE_UNTIL / 5 + (n - DENSE_UNTIL) / SENTENCES_PER_EXTRA_POINT)
            .coerceAtLeast(2)

        // Özet cümlesi: belgenin başındaki (vurgu etiketsiz) en yüksek puanlı cümle, genellikle konunun tanımı.
        val headline = sentences.take(3).filter { it.label == null }.maxByOrNull { scored.getValue(it) } ?: sentences.first()
        val chosen = mutableListOf(headline)
        if (sectioned) {
            val others = sentences.filter { it !== headline }.sortedByDescending { inSection.getValue(it) }
            val total = maxOf(pointCount, bySection.size * POINTS_PER_SECTION).coerceAtMost(pointCount * MAX_POINT_SHARE)
            val quota = quotas(bySection.mapValues { it.value.size }, total)
            for ((section, want) in quota) {
                var taken = 0
                for (candidate in others) {
                    if (taken == want) break
                    if (candidate.section != section) continue
                    if (chosen.none { similarity(it.stems, candidate.stems) > SIMILARITY_LIMIT }) {
                        chosen += candidate
                        taken++
                    }
                }
            }
        } else {
            val others = sentences.filter { it !== headline }.sortedByDescending { scored.getValue(it) }
            for (candidate in others) {
                if (chosen.size == pointCount + 1) break
                if (chosen.none { similarity(it.stems, candidate.stems) > SIMILARITY_LIMIT }) chosen += candidate
            }
        }

        // Seçilenler belgedeki sırasıyla okunur: ilki "Özet" (çoğunlukla giriş cümlesi), kalanları maddeler.
        val points = chosen.filter { it !== headline }.sortedBy { it.index }
        return buildString {
            append("Özet: ").append(clip(headline.text))
            if (points.isEmpty()) return@buildString
            if (sectioned) {
                var current = -1
                for (point in points) {
                    if (point.section != current) {
                        current = point.section
                        val title = parsed.titles[current]
                        if (title.isNotEmpty()) append("\n\n## ").append(prettyTitle(title)) else append('\n')
                    }
                    append("\n- ").append(clip(withLabel(point)))
                }
            } else {
                append("\n\nÖnemli noktalar:")
                points.forEach { append("\n- ").append(clip(withLabel(it))) }
            }
        }
    }

    /** Her konuya en az 1, kalanı büyüklüğüne orantılı (en çok [MAX_POINTS_PER_SECTION]) kontenjan verir; toplam [total]. */
    private fun quotas(sizes: Map<Int, Int>, total: Int): Map<Int, Int> {
        val quota = LinkedHashMap<Int, Int>()
        sizes.keys.forEach { quota[it] = 1 }
        var left = total - sizes.size
        while (left > 0) {
            // Bölüştürme: kontenjanı doldurulmamış ve cümlesi yetecek konulardan, kişi başı en çok cümle düşeni büyüt.
            val next = quota.keys
                .filter { quota.getValue(it) < MAX_POINTS_PER_SECTION && quota.getValue(it) < sizes.getValue(it) }
                .maxByOrNull { sizes.getValue(it).toDouble() / (quota.getValue(it) + 1) } ?: break
            quota[next] = quota.getValue(next) + 1
            left--
        }
        return quota
    }

    private fun withLabel(sentence: Sentence): String = if (sentence.label == null) sentence.text else sentence.label + ": " + sentence.text

    /** BÜYÜK HARFLİ başlığı "Büyük harfli" yapar; karışık harfli başlığa dokunmaz. */
    private fun prettyTitle(title: String): String {
        val letters = title.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() } || letters.matches(Regex("[IVXLCDM]+"))) return title
        val lower = title.lowercase(turkish)
        return lower.replaceFirstChar { it.titlecase(turkish) }
    }

    // --- Temizlik ---------------------------------------------------------------------------------------

    /** Sayfa altbilgilerini, tekrar eden üst/alt bilgileri, bağlantı/kullanıcı adı satırlarını atar; bölünmüş büyük harfleri onarır. */
    internal fun clean(text: String): String {
        val lines = text.lines().map { it.trim() }
        val repeated = lines.filter { it.isNotEmpty() && it.split(Regex("\\s+")).size <= HEADING_MAX_WORDS * 2 }
            .groupingBy { it.replace(Regex("\\d+"), "#") }.eachCount()
            .filter { it.value >= REPEATED_LINE_MIN }.keys
        return lines.filterNot { line ->
            line.isNotEmpty() && (pageNumberLine.matches(line) || noiseLine.containsMatchIn(line) ||
                line.replace(Regex("\\d+"), "#") in repeated)
        }.joinToString("\n") { repairSplitCapitals(it) }
    }

    /** "M ekanik" -> "Mekanik" (PDF çıkarımı büyük harften sonra boşluk koyabiliyor). Sık kelime ya da "vitamin" gelirse dokunmaz. */
    internal fun repairSplitCapitals(line: String): String =
        splitCapital.replace(line) { m ->
            val rest = m.groupValues[2]
            if (rest.lowercase(turkish) in STOPWORDS || rest.startsWith("vitamin")) m.value else m.groupValues[1] + rest
        }

    // --- Cümlelere ayırma -------------------------------------------------------------------------------

    /**
     * Metin satırı başlık mı: kısa, noktalamayla bitmeyen ve BÜYÜK HARFLİ ya da numaralı ("2. Öğrenme Hedefleri") satır.
     * Yalnızca baş harfleri büyük satırlar (özel isim listesi, künye, kesik satır) başlık sayılmaz: yanlış başlık, özeti
     * anlamsız konulara böler.
     */
    internal fun isHeading(line: String): Boolean {
        val raw = line.trim().replace(Regex("^[\\uE000-\\uF8FF•●▪○·\\-*]+\\s*"), "")
        if (raw.isEmpty() || raw.length > HEADING_MAX_CHARS || calloutLabel.containsMatchIn(raw)) return false
        val words = raw.split(Regex("\\s+"))
        if (words.size > HEADING_MAX_WORDS || raw.last() in ".!?;,:…") return false
        if (numberedHeading.matches(raw)) return words.size >= 2
        val letters = raw.filter { it.isLetter() }
        val digits = raw.count { it.isDigit() }
        return letters.length >= 3 && digits <= letters.length && letters.count { it.isUpperCase() } >= letters.length * 0.75
    }

    /** Segmentlerden başlık olanlar bölüm başlatır; cümleler (belgedeki sırasıyla) kendi bölüm numarasını taşır. */
    private fun parse(text: String): Parsed {
        val titles = mutableListOf("")
        var section = 0
        val pieces = mutableListOf<Piece>()
        for (segment in segments(text)) {
            if (isHeading(segment)) {
                section = titles.size
                titles += segment.replace(listMarker, "").trim()
                continue
            }
            splitSentences(segment).forEachIndexed { i, s ->
                val plain = s.replace(listMarker, "").trim()
                val label = calloutLabel.find(plain)
                val body = if (label != null) plain.substring(label.value.length).trim() else plain
                pieces += Piece(body, label?.groupValues?.get(1)?.let(::labelName), i == 0, wordCount(s), section)
            }
        }
        for (minWords in intArrayOf(MIN_WORDS, 2)) {
            val kept = pieces.filter { it.words >= minWords && it.text.any { c -> c.isLetter() } }
            if (kept.isNotEmpty()) {
                val sentences = kept.mapIndexed { index, p ->
                    Sentence(index, p.text, stemsOf(p.text), p.paragraphStart, p.words, p.section, p.label)
                }
                return Parsed(sentences, titles)
            }
        }
        return Parsed(emptyList(), titles)
    }

    private class Piece(val text: String, val label: String?, val paragraphStart: Boolean, val words: Int, val section: Int)

    private fun labelName(raw: String): String = raw.lowercase(turkish).replaceFirstChar { it.titlecase(turkish) }

    /**
     * Metni paragraflara böler. Boş satır paragraf sonudur. PDF'ler satırları görsel olarak böler: uzun ve noktalamayla
     * bitmeyen bir satırın sonrakiyle birleştirilmesi gerekir (satır sonundaki tire ile bölünmüş kelime de birleşir).
     * Madde işaretiyle başlayan satır, başlık ve kısa satır yeni bir parça başlatır.
     */
    internal fun segments(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var previous = ""

        fun flush() {
            if (current.isNotBlank()) result += current.toString().trim()
            current.clear()
        }

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                flush()
                previous = ""
                continue
            }
            val continues = current.isNotEmpty() && previous.length >= WRAPPED_LINE_MIN &&
                previous.last() !in ".!?:;…" && !listMarker.containsMatchIn(line) && !isHeading(line)
            if (continues) {
                if (previous.endsWith("-")) current.setLength(current.length - 1) else current.append(' ')
            } else {
                flush()
            }
            current.append(line)
            previous = line
        }
        flush()
        return result
    }

    /** Bir paragrafı cümlelere böler: . ! ? … sonrası boşluk gelirse; kısaltma, baş harf ve numaralarda bölünmez. */
    internal fun splitSentences(segment: String): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                var end = i + 1
                while (end < segment.length && segment[end] in ".!?…\"”')]»") end++
                var next = end
                while (next < segment.length && segment[next].isWhitespace()) next++
                val boundary = next > end &&
                    (c != '.' || (!isAbbreviation(segment, start, i) && !segment[next].isLowerCase()))
                if (boundary || end >= segment.length) {
                    segment.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(out::add)
                    start = next
                }
                i = maxOf(next, end)
                continue
            }
            i++
        }
        if (start < segment.length) segment.substring(start).trim().takeIf { it.isNotEmpty() }?.let(out::add)
        return out
    }

    /** Noktadan önceki kelime bir kısaltma, tek harfli baş harf ya da madde numarası mı? ("Dr.", "A.", "3.") */
    private fun isAbbreviation(segment: String, sentenceStart: Int, dot: Int): Boolean {
        var wordStart = dot
        while (wordStart > sentenceStart && !segment[wordStart - 1].isWhitespace()) wordStart--
        val word = segment.substring(wordStart, dot).trim('(', '"', '“', '\'')
        if (word.length == 1 && word[0].isLetter()) return true
        if (word.length <= 2 && word.all { it.isDigit() }) return true
        return word.lowercase(turkish) in ABBREVIATIONS
    }

    private fun wordCount(s: String): Int = s.split(Regex("\\s+")).count { it.isNotEmpty() }

    // --- Puanlama ----------------------------------------------------------------------------------------

    private fun stemsOf(sentence: String): Set<String> =
        wordRegex.findAll(sentence.replace(suffixAfterApostrophe, "").lowercase(turkish))
            .map { it.value }
            .filter { it.length >= 3 && it !in STOPWORDS }
            .map { it.take(STEM_LENGTH) }
            .toSet()

    /** Kelime ağırlığı: df x (1 - df / (n + 1)); df = kelimenin geçtiği cümle sayısı, n = toplam cümle sayısı. */
    private fun termWeights(sentences: List<Sentence>): Map<String, Double> {
        val documentFrequency = HashMap<String, Int>()
        for (sentence in sentences) {
            for (stem in sentence.stems) documentFrequency.merge(stem, 1, Int::plus)
        }
        val n = sentences.size.toDouble()
        return documentFrequency.mapValues { (_, df) -> df * (1.0 - df / (n + 1.0)) }
    }

    /**
     * Konu içi kelime ağırlığı: kelimenin o konudaki cümle sayısı x ln((konu sayısı + 1) / kelimeyi içeren konu sayısı).
     * Her konuda geçen genel kelimeler ("sindirim") sönük kalır; bir konuda tekrar eden özel kelimeler ("pepsin", "öz suyu")
     * o konunun asıl cümlelerini öne çıkarır.
     */
    private fun sectionTermWeights(bySection: Map<Int, List<Sentence>>): Map<Int, Map<String, Double>> {
        val sectionsWithStem = HashMap<String, Int>()
        val frequency = bySection.mapValues { (_, list) ->
            HashMap<String, Int>().also { m -> list.forEach { s -> s.stems.forEach { m.merge(it, 1, Int::plus) } } }
        }
        frequency.values.forEach { m -> m.keys.forEach { sectionsWithStem.merge(it, 1, Int::plus) } }
        val count = bySection.size + 1.0
        return frequency.mapValues { (_, m) -> m.mapValues { (stem, df) -> df * kotlin.math.ln(count / sectionsWithStem.getValue(stem)) } }
    }

    private fun baseScore(sentence: Sentence, weights: Map<String, Double>): Double {
        if (sentence.stems.isEmpty()) return 0.0
        return sentence.stems.sumOf { weights.getValue(it) } / sentence.stems.size.toDouble().pow(0.7)
    }

    /** Cümlenin türüne göre çarpan: belge/paragraf başı, tanım, vurgulu not, önceki cümleye bağlı başlangıç, aşırı uzunluk. */
    private fun multiplier(sentence: Sentence): Double {
        var m = 1.0
        if (sentence.index == 0) m *= 1.5
        else if (sentence.paragraphStart) m *= 1.2
        if (definition.containsMatchIn(sentence.text)) m *= 1.35
        if (sentence.label != null) m *= 1.25
        if (dependentStart.containsMatchIn(sentence.text)) m *= 0.75
        if (sentence.words > 50) m *= 0.85
        return m
    }

    private fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.count { it in b }
        return common.toDouble() / (a.size + b.size - common)
    }

    private fun clip(text: String): String {
        if (text.length <= MAX_SENTENCE_CHARS) return text
        val cut = text.lastIndexOf(' ', MAX_SENTENCE_CHARS).takeIf { it > MAX_SENTENCE_CHARS / 2 } ?: MAX_SENTENCE_CHARS
        return text.substring(0, cut).trimEnd(',', ';', ':', '-') + "…"
    }

    private val ABBREVIATIONS = setOf(
        "dr", "prof", "doç", "yrd", "öğr", "gör", "uzm", "av", "müh", "mim", "dt", "sn", "sy", "no", "tel", "bkz", "vb",
        "vs", "vd", "örn", "yy", "mah", "cad", "sok", "apt", "blv", "ltd", "şti", "a.ş", "san", "tic", "çev", "ed", "haz",
        "yay", "krş", "age", "agm", "yönt", "tc",
    )

    private val STOPWORDS = setOf(
        "ve", "veya", "ile", "ama", "fakat", "ancak", "lakin", "çünkü", "ki", "da", "de", "mi", "mı", "mu", "mü", "bir",
        "bu", "şu", "bunu", "şunu", "onu", "bunlar", "şunlar", "onlar", "bunun", "onun", "bunda", "bunlara", "için",
        "gibi", "kadar", "daha", "çok", "pek", "hem", "ise", "nasıl", "neden", "niçin", "niye", "hangi", "her", "bazı",
        "tüm", "bütün", "hiç", "hiçbir", "olarak", "olan", "olanlar", "olduğu", "olduğunu", "oldu", "olur", "olabilir",
        "olması", "olmak", "olsun", "etmek", "edilen", "edilir", "edilmesi", "yapılan", "yapılır", "yapılması", "yapmak",
        "var", "yok", "değil", "ben", "sen", "biz", "siz", "beni", "seni", "bizi", "sizi", "bana", "sana", "bize", "size",
        "benim", "senin", "bizim", "sizin", "kendi", "kendisi", "sonra", "önce", "şey", "şekilde", "göre", "ayrıca",
        "yani", "hatta", "üzere", "dolayı", "nedeniyle", "karşı", "rağmen", "arasında", "içinde", "üzerinde", "altında",
        "ayrı", "aynı", "diğer", "başka", "yine", "ise", "şöyle", "böyle", "öyle", "bile", "ise", "sadece", "yalnızca",
        "ancak", "zaman", "sırasında", "tarafından", "ilgili", "söz", "konusu", "halde", "kez", "defa", "den", "dan",
    )
}
