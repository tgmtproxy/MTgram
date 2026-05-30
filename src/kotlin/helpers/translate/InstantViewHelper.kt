package desu.inugram.helpers.translate

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import desu.inugram.InuConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LanguageDetector
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ArticleViewer
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.TextPaintUrlSpan
import org.telegram.ui.Components.TextPaintWebpageUrlSpan
import org.telegram.ui.Components.TranslateAlert2
import org.telegram.ui.RestrictedLanguagesSelectActivity
import java.util.WeakHashMap

object InstantViewHelper {
    const val MENU_ITEM_ID = 100

    private const val COCOON_SUMMARY_CAPTION = "Cocoon AI Summary"

    private const val MAX_SYMBOLS_PER_REQUEST = 25_000
    private const val MAX_ENTRIES_PER_REQUEST = 20
    private const val MAX_SYMBOLS_PER_ENTRY = 4_000
    private const val SAMPLE_LIMIT = 2_000

    @JvmStatic
    fun shouldHideBlock(block: TLRPC.PageBlock?): Boolean {
        if (InuConfig.HIDE_IV_SUMMARY.value && block is TLRPC.TL_pageBlockBlockquote) {
            val caption = ArticleViewer.getPlainText(block.caption) ?: return false
            return caption.toString() == COCOON_SUMMARY_CAPTION
        }

        return false
    }

    private class State(val original: TLRPC.WebPage) {
        val richCache = HashMap<TLRPC.RichText, CharSequence>()
        val stringCache = HashMap<String, String>()
        var applied = false
        var loading = false
        var unavailable = false
        var detectedSrcLang: String? = null
        var detectionDone = false
        var detectionInFlight = false
    }

    private val states = WeakHashMap<ArticleViewer, MutableMap<Long, State>>()

    private fun stateFor(viewer: ArticleViewer, page: TLRPC.WebPage): State {
        val map = states.getOrPut(viewer) { mutableMapOf() }
        return map.getOrPut(page.id) { State(page) }
    }

    private fun activeStateFor(viewer: ArticleViewer?): State? {
        if (viewer == null) return null
        val page = viewer.currentPageLayout?.adapter?.currentPage ?: return null
        return states[viewer]?.get(page.id)
    }

    /** For TL_textPlain root inside getText() and for plain-string fields. */
    @JvmStatic
    fun translate(viewer: ArticleViewer?, original: CharSequence?): CharSequence? {
        if (original.isNullOrEmpty()) return original
        val st = activeStateFor(viewer)
        if (st == null || !st.applied) return original
        return st.stringCache[original.toString()] ?: original
    }

    /** Top-level rich-text root substitution; returns cached styled CharSequence or null. */
    @JvmStatic
    fun lookupRich(viewer: ArticleViewer?, richText: TLRPC.RichText?): CharSequence? {
        if (richText == null) return null
        val state = activeStateFor(viewer) ?: return null
        if (!state.applied) return null
        return state.richCache[richText]
    }

    @JvmStatic
    fun onPageOpened(viewer: ArticleViewer?, page: TLRPC.WebPage?) {
        if (viewer == null || page?.cached_page == null) return
        ensureDetected(stateFor(viewer, page))
    }

    @JvmStatic
    fun addMenuItem(o: ItemOptions, click: Runnable, viewer: ArticleViewer?) {
        if (viewer == null) return
        val page = viewer.currentPageLayout?.adapter?.currentPage ?: return
        val state = stateFor(viewer, page)
        if (!state.detectionDone || state.unavailable) return

        val label = LocaleController.getString(
            when {
                state.loading -> R.string.InuTranslating
                state.applied -> R.string.InuInstantViewShowOriginal
                else -> R.string.InuTranslateInstantView
            }
        )
        val icon = if (state.applied) R.drawable.msg_reset else R.drawable.msg_translate
        o.add(icon, label, click)
        if (state.loading) {
            o.getLast()?.let {
                it.isEnabled = false
                it.alpha = 0.5f
            }
        }
    }

    private fun ensureDetected(state: State) {
        if (state.detectionDone || state.detectionInFlight) return
        detectLanguage(state) { src ->
            val toLang = TranslateAlert2.getToLanguage()
            if (src == null || toLang == null) return@detectLanguage
            val dnt = RestrictedLanguagesSelectActivity.getRestrictedLanguages()
            if (src in dnt || src == toLang) state.unavailable = true
        }
    }

    private fun detectLanguage(state: State, onDone: (String?) -> Unit) {
        if (!LanguageDetector.hasSupport()) {
            state.detectionDone = true
            onDone(null)
            return
        }
        val sample = collectSample(state.original)
        if (sample.isBlank()) {
            state.detectionDone = true
            onDone(null)
            return
        }
        state.detectionInFlight = true
        val finish: (String?) -> Unit = { src ->
            AndroidUtilities.runOnUIThread {
                state.detectedSrcLang = src
                state.detectionDone = true
                state.detectionInFlight = false
                onDone(src)
            }
        }
        LanguageDetector.detectLanguage(
            sample,
            { detected -> finish(detected?.split("_")?.firstOrNull()) },
            { finish(null) },
        )
    }

    @JvmStatic
    fun onClick(viewer: ArticleViewer) {
        val page = viewer.currentPageLayout?.adapter?.currentPage ?: return
        val st = stateFor(viewer, page)
        if (st.loading) return
        if (st.applied) {
            st.applied = false
            invalidatePages(viewer)
            return
        }
        if (st.richCache.isNotEmpty() || st.stringCache.isNotEmpty()) {
            st.applied = true
            invalidatePages(viewer)
            return
        }
        startTranslate(viewer, st)
    }

    private fun startTranslate(viewer: ArticleViewer, state: State) {
        val toLang = TranslateAlert2.getToLanguage()
        if (toLang.isNullOrEmpty()) return

        val proceed: (String?) -> Unit = { src ->
            if (src == null || !checkDnt(viewer, state, src, toLang)) {
                launchRequests(viewer, state, toLang)
            }
        }

        if (state.detectionDone) {
            proceed(state.detectedSrcLang)
            return
        }

        state.loading = true
        startProgress(viewer)

        detectLanguage(state, proceed)
    }

    private fun startProgress(viewer: ArticleViewer) {
        val p = viewer.actionBar?.lineProgressView ?: return
        p.setProgress(0f, false)
        p.setProgress(0.1f, true)
    }

    private fun invalidatePages(viewer: ArticleViewer) {
        val pages = viewer.pages ?: return
        for (page in pages) {
            val adapter = page?.adapter ?: continue
            adapter.resetCachedHeights()
            adapter.notifyDataSetChanged()
        }
    }

    private fun checkDnt(viewer: ArticleViewer, state: State, src: String, toLang: String): Boolean {
        val dnt = RestrictedLanguagesSelectActivity.getRestrictedLanguages()
        if (src !in dnt && src != toLang) return false
        state.loading = false
        state.unavailable = true
        finishProgress(viewer)
        bulletin(viewer)
            .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.InuTranslateInstantViewSkipped))
            .show()
        return true
    }

    private sealed class Job(val text: TLRPC.TL_textWithEntities) {
        class Rich(
            val root: TLRPC.RichText,
            text: TLRPC.TL_textWithEntities,
            val webpageUrls: Set<String>,
        ) : Job(text)

        class Str(val source: String) : Job(TLRPC.TL_textWithEntities().apply { text = source })
    }

    private fun launchRequests(viewer: ArticleViewer, state: State, toLang: String) {
        val jobs = mutableListOf<Job>()
        collectJobs(state, state.original, jobs)
        if (jobs.isEmpty()) {
            state.loading = false
            finishProgress(viewer)
            if (state.richCache.isEmpty() && state.stringCache.isEmpty()) {
                state.unavailable = true
            } else {
                state.applied = true
                invalidatePages(viewer)
            }
            return
        }

        val translatable = jobs.filter { it.text.text.length <= MAX_SYMBOLS_PER_ENTRY }
        val skippedAnyOversize = translatable.size != jobs.size
        if (translatable.isEmpty()) {
            state.loading = false
            finishProgress(viewer)
            failBulletin(viewer)
            return
        }

        val chunks = mutableListOf<List<Job>>()
        var current = mutableListOf<Job>()
        var sum = 0
        for (j in translatable) {
            val len = j.text.text.length
            if (current.isNotEmpty() &&
                (sum + len > MAX_SYMBOLS_PER_REQUEST || current.size >= MAX_ENTRIES_PER_REQUEST)
            ) {
                chunks += current
                current = mutableListOf()
                sum = 0
            }
            current += j
            sum += len
        }
        if (current.isNotEmpty()) chunks += current

        state.loading = true
        state.applied = true
        startProgress(viewer)

        val total = chunks.size
        val pending = intArrayOf(total)
        val anyFail = booleanArrayOf(skippedAnyOversize)
        val anySuccess = booleanArrayOf(false)
        val account = viewer.currentAccount

        for (chunk in chunks) {
            val req = TLRPC.TL_messages_translateText()
            req.flags = req.flags or 2
            req.to_lang = toLang
            for (j in chunk) req.text.add(j.text)
            ConnectionsManager.getInstance(account).sendRequest(req) { res, _ ->
                AndroidUtilities.runOnUIThread {
                    if (res is TLRPC.TL_messages_translateResult && res.result.size >= chunk.size) {
                        anySuccess[0] = true
                        for (i in chunk.indices) {
                            val translated = res.result[i]
                            val text = translated.text ?: continue
                            if (text.isEmpty()) continue
                            when (val j = chunk[i]) {
                                is Job.Rich -> {
                                    val styled = SpannableStringBuilder(text)
                                    val ents = translated.entities
                                    if (!ents.isNullOrEmpty()) {
                                        MessageObject.addEntitiesToText(styled, ents, false, false, false, true)
                                    }
                                    rewireLinkSpans(styled, viewer, j.webpageUrls)
                                    state.richCache[j.root] = styled
                                }

                                is Job.Str -> state.stringCache[j.source] = text
                            }
                        }
                        invalidatePages(viewer)
                    } else {
                        anyFail[0] = true
                    }
                    pending[0]--
                    val progress = (total - pending[0]).toFloat() / total
                    viewer.actionBar?.lineProgressView?.setProgress(progress.coerceAtLeast(0.1f), true)
                    if (pending[0] != 0) return@runOnUIThread
                    state.loading = false
                    finishProgress(viewer)
                    if (!anySuccess[0] && state.richCache.isEmpty() && state.stringCache.isEmpty()) {
                        state.applied = false
                        invalidatePages(viewer)
                        failBulletin(viewer)
                        return@runOnUIThread
                    }
                    if (anyFail[0]) {
                        bulletin(viewer)
                            .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.InuTranslateInstantViewPartial))
                            .show()
                    }
                }
            }
        }
    }

    private fun finishProgress(viewer: ArticleViewer) {
        viewer.actionBar?.lineProgressView?.setProgress(1f, true)
    }

    private fun rewireLinkSpans(
        styled: SpannableStringBuilder,
        viewer: ArticleViewer,
        webpageUrls: Set<String>,
    ) {
        val urlSpans = styled.getSpans(0, styled.length, URLSpan::class.java) ?: return
        if (urlSpans.isEmpty()) return
        val linkColor = Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, viewer.resourcesProvider)
        for (span in urlSpans) {
            val start = styled.getSpanStart(span)
            val end = styled.getSpanEnd(span)
            val url = span.url ?: continue
            styled.removeSpan(span)
            styled.setSpan(ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val urlSpan = if (url in webpageUrls) {
                TextPaintWebpageUrlSpan(null, url)
            } else {
                TextPaintUrlSpan(null, url)
            }
            styled.setSpan(urlSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun bulletin(viewer: ArticleViewer): BulletinFactory =
        viewer.currentPageLayout?.let { BulletinFactory.of(it, viewer.resourcesProvider) }
            ?: BulletinFactory.global()

    private fun failBulletin(viewer: ArticleViewer) {
        bulletin(viewer)
            .createErrorBulletin(LocaleController.getString(R.string.TranslationFailedAlert1))
            .show()
    }

    private fun collectSample(page: TLRPC.WebPage?): String {
        val sb = StringBuilder()
        val blocks = page?.cached_page?.blocks ?: return ""
        val visitor = object : Visitor {
            override fun rich(root: TLRPC.RichText): Boolean {
                appendPlain(root, sb)
                sb.append(' ')
                return sb.length < SAMPLE_LIMIT
            }

            override fun str(s: String): Boolean {
                sb.append(s).append(' ')
                return sb.length < SAMPLE_LIMIT
            }
        }
        for (block in blocks) if (!walkBlock(block, visitor)) break
        return sb.toString().trim()
    }

    private fun appendPlain(rt: TLRPC.RichText?, sb: StringBuilder) {
        if (rt == null) return
        when (rt) {
            is TLRPC.TL_textPlain -> rt.text?.let { sb.append(it) }
            is TLRPC.TL_textConcat -> rt.texts.forEach { appendPlain(it, sb) }
            is TLRPC.TL_textFixed -> appendPlain(rt.text, sb)
            is TLRPC.TL_textItalic -> appendPlain(rt.text, sb)
            is TLRPC.TL_textBold -> appendPlain(rt.text, sb)
            is TLRPC.TL_textUnderline -> appendPlain(rt.text, sb)
            is TLRPC.TL_textStrike -> appendPlain(rt.text, sb)
            is TLRPC.TL_textEmail -> appendPlain(rt.text, sb)
            is TLRPC.TL_textPhone -> appendPlain(rt.text, sb)
            is TLRPC.TL_textUrl -> appendPlain(rt.text, sb)
            is TLRPC.TL_textAnchor -> appendPlain(rt.text, sb)
            is TLRPC.TL_textSubscript -> appendPlain(rt.text, sb)
            is TLRPC.TL_textSuperscript -> appendPlain(rt.text, sb)
            is TLRPC.TL_textMarked -> appendPlain(rt.text, sb)
            else -> {}
        }
    }

    private fun collectJobs(st: State, page: TLRPC.WebPage?, out: MutableList<Job>) {
        val blocks = page?.cached_page?.blocks ?: return
        val seenRoots = HashSet<TLRPC.RichText>()
        val seenStrings = HashSet<String>()
        val visitor = object : Visitor {
            override fun rich(root: TLRPC.RichText): Boolean {
                if (st.richCache.containsKey(root)) return true
                if (!seenRoots.add(root)) return true
                val ex = buildRichExtract(root) ?: return true
                out += Job.Rich(root, ex.twe, ex.webpageUrls)
                return true
            }

            override fun str(s: String): Boolean {
                if (st.stringCache.containsKey(s)) return true
                if (!seenStrings.add(s)) return true
                out += Job.Str(s)
                return true
            }
        }
        for (block in blocks) walkBlock(block, visitor)
    }

    private interface Visitor {
        fun rich(root: TLRPC.RichText): Boolean
        fun str(s: String): Boolean
    }

    /** Visit each top-level RichText and plain-string field in [block]. */
    private fun walkBlock(block: TLRPC.PageBlock?, visitor: Visitor): Boolean {
        if (block == null) return true
        return when (block) {
            is TLRPC.TL_pageBlockTitle -> visitRich(block.text, visitor)
            is TLRPC.TL_pageBlockSubtitle -> visitRich(block.text, visitor)
            is TLRPC.TL_pageBlockHeader -> visitRich(block.text, visitor)
            is TLRPC.TL_pageBlockSubheader -> visitRich(block.text, visitor)
            is TLRPC.TL_pageBlockKicker -> visitRich(block.text, visitor)
            is TLRPC.TL_pageBlockFooter -> visitRich(block.text, visitor)
            is TLRPC.TL_pageBlockParagraph -> visitRich(block.text, visitor)
            is TLRPC.TL_pageBlockPreformatted -> visitRich(block.text, visitor)
            is TLRPC.TL_pageBlockBlockquote ->
                visitRich(block.text, visitor) && visitRich(block.caption, visitor)

            is TLRPC.TL_pageBlockPullquote ->
                visitRich(block.text, visitor) && visitRich(block.caption, visitor)

            is TLRPC.TL_pageBlockAuthorDate -> visitRich(block.author, visitor)
            is TLRPC.TL_pageBlockPhoto -> visitCaption(block.caption, visitor)
            is TLRPC.TL_pageBlockVideo -> visitCaption(block.caption, visitor)
            is TLRPC.TL_pageBlockAudio -> visitCaption(block.caption, visitor)
            is TLRPC.TL_pageBlockEmbed -> visitCaption(block.caption, visitor)
            is TLRPC.TL_pageBlockMap -> visitCaption(block.caption, visitor)
            is TLRPC.TL_pageBlockSlideshow -> {
                if (!visitCaption(block.caption, visitor)) return false
                for (item in block.items) if (!walkBlock(item, visitor)) return false
                true
            }

            is TLRPC.TL_pageBlockCollage -> {
                if (!visitCaption(block.caption, visitor)) return false
                for (item in block.items) if (!walkBlock(item, visitor)) return false
                true
            }

            is TLRPC.TL_pageBlockEmbedPost -> {
                val author = block.author
                if (!author.isNullOrBlank() && !visitor.str(author)) return false
                for (inner in block.blocks) if (!walkBlock(inner, visitor)) return false
                visitCaption(block.caption, visitor)
            }

            is TLRPC.TL_pageBlockCover -> walkBlock(block.cover, visitor)
            is TLRPC.TL_pageBlockDetails -> {
                if (!visitRich(block.title, visitor)) return false
                for (inner in block.blocks) if (!walkBlock(inner, visitor)) return false
                true
            }

            is TLRPC.TL_pageBlockTable -> {
                if (!visitRich(block.title, visitor)) return false
                for (row in block.rows) {
                    for (cell in row.cells) if (!visitRich(cell.text, visitor)) return false
                }
                true
            }

            is TLRPC.TL_pageBlockList -> {
                for (item in block.items) {
                    when (item) {
                        is TLRPC.TL_pageListItemText -> if (!visitRich(item.text, visitor)) return false
                        is TLRPC.TL_pageListItemBlocks -> for (b in item.blocks) if (!walkBlock(b, visitor)) return false
                    }
                }
                true
            }

            is TLRPC.TL_pageBlockOrderedList -> {
                for (item in block.items) {
                    when (item) {
                        is TLRPC.TL_pageListOrderedItemText -> if (!visitRich(item.text, visitor)) return false
                        is TLRPC.TL_pageListOrderedItemBlocks -> for (b in item.blocks) if (!walkBlock(b, visitor)) return false
                    }
                }
                true
            }

            is TLRPC.TL_pageBlockRelatedArticles -> {
                if (!visitRich(block.title, visitor)) return false
                for (article in block.articles) {
                    article.title?.takeIf { it.isNotBlank() }?.let { if (!visitor.str(it)) return false }
                    article.description?.takeIf { it.isNotBlank() }?.let { if (!visitor.str(it)) return false }
                    article.author?.takeIf { it.isNotBlank() }?.let { if (!visitor.str(it)) return false }
                }
                true
            }

            else -> true
        }
    }

    private fun visitRich(rt: TLRPC.RichText?, visitor: Visitor): Boolean {
        if (rt == null || rt is TLRPC.TL_textEmpty) return true
        return visitor.rich(rt)
    }

    private fun visitCaption(cap: TLRPC.TL_pageCaption?, visitor: Visitor): Boolean {
        if (cap == null) return true
        if (!visitRich(cap.text, visitor)) return false
        return visitRich(cap.credit, visitor)
    }

    private class RichExtract(
        val twe: TLRPC.TL_textWithEntities,
        val webpageUrls: Set<String>,
    )

    private fun buildRichExtract(root: TLRPC.RichText): RichExtract? {
        val b = RichExtractBuilder()
        b.append(root)
        return b.build()
    }

    /**
     * Walks a top-level RichText into a TextWithEntities preserving formatting boundaries,
     * so the server sees the full sentence with markup as context. Also collects urls whose
     * source TL_textUrl carried a webpage preview, so [rewireLinkSpans] can restore the
     * IV-preview accent after translation.
     */
    private class RichExtractBuilder {
        private val sb = StringBuilder()
        private val ents = ArrayList<TLRPC.MessageEntity>()
        private val webpageUrls = HashSet<String>()

        fun build(): RichExtract? {
            if (sb.isEmpty()) return null
            val twe = TLRPC.TL_textWithEntities()
            twe.text = sb.toString()
            twe.entities = ents
            return RichExtract(twe, webpageUrls)
        }

        fun append(rt: TLRPC.RichText?) {
            if (rt == null) return
            when (rt) {
                is TLRPC.TL_textEmpty -> {}
                is TLRPC.TL_textPlain -> rt.text?.let { sb.append(it) }
                is TLRPC.TL_textConcat -> rt.texts.forEach { append(it) }
                is TLRPC.TL_textBold -> wrap(rt.text) { TLRPC.TL_messageEntityBold() }
                is TLRPC.TL_textItalic -> wrap(rt.text) { TLRPC.TL_messageEntityItalic() }
                is TLRPC.TL_textUnderline -> wrap(rt.text) { TLRPC.TL_messageEntityUnderline() }
                is TLRPC.TL_textStrike -> wrap(rt.text) { TLRPC.TL_messageEntityStrike() }
                is TLRPC.TL_textFixed -> wrap(rt.text) { TLRPC.TL_messageEntityCode() }
                is TLRPC.TL_textUrl -> {
                    val u = rt.url ?: ""
                    if (rt.webpage_id != 0L && u.isNotEmpty()) webpageUrls += u
                    wrap(rt.text) { TLRPC.TL_messageEntityTextUrl().apply { url = u } }
                }

                is TLRPC.TL_textEmail -> wrap(rt.text) { TLRPC.TL_messageEntityEmail() }
                is TLRPC.TL_textPhone -> wrap(rt.text) { TLRPC.TL_messageEntityPhone() }
                is TLRPC.TL_textAnchor -> append(rt.text)
                is TLRPC.TL_textSubscript -> append(rt.text)
                is TLRPC.TL_textSuperscript -> append(rt.text)
                is TLRPC.TL_textMarked -> append(rt.text)
                else -> {}
            }
        }

        private inline fun wrap(child: TLRPC.RichText?, make: () -> TLRPC.MessageEntity) {
            val start = sb.length
            append(child)
            val len = sb.length - start
            if (len > 0) {
                ents += make().apply {
                    offset = start
                    length = len
                }
            }
        }
    }
}
