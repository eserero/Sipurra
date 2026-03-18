@file:OptIn(ExperimentalReadiumApi::class, InternalReadiumApi::class)

package com.storyteller.reader

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import kotlin.math.ceil

data class Highlight(val id: String, @ColorInt val color: Int, val locator: Locator)

data class CustomFont(val uri: String, val name: String, val type: String)

data class Props(
    var bookUuid: String?,
    var locator: Locator?,
    var isPlaying: Boolean?,
    var highlights: List<Highlight>?,
    var bookmarks: List<Locator>?,
    var readaloudColor: Int?,
    var customFonts: List<CustomFont>?,
    @ColorInt var foreground: Int?,
    @ColorInt var background: Int?,
    var fontFamily: FontFamily?,
    var lineHeight: Double?,
    var paragraphSpacing: Double?,
    var fontSize: Double?,
    var textAlign: TextAlign?
)


data class FinalizedProps(
    var bookUuid: String,
    var locator: Locator?,
    var isPlaying: Boolean,
    var highlights: List<Highlight>,
    var bookmarks: List<Locator>,
    var readaloudColor: Int,
    var customFonts: List<CustomFont>,
    @ColorInt var foreground: Int,
    @ColorInt var background: Int,
    var fontFamily: FontFamily,
    var lineHeight: Double,
    var paragraphSpacing: Double,
    var fontSize: Double,
    var textAlign: TextAlign
)

/**
 * Listener interface for EPUB reader events. All methods have default no-op implementations
 * so you only need to override the ones you care about.
 */
interface EpubViewListener {
    fun onLocatorChange(locator: Locator) {}
    fun onMiddleTouch() {}
    fun onSelection(locator: Locator, x: Int, y: Int) {}
    fun onSelectionCleared() {}
    fun onDoubleTouch(locator: Locator) {}
    fun onHighlightTap(decorationId: String, x: Int, y: Int) {}
    fun onBookmarksActivate(activeBookmarks: List<Locator>) {}
}

/**
 * A [FrameLayout] that hosts a Readium EPUB navigator with synchronized audio highlighting.
 *
 * @param context Android context.
 * @param activity The [FragmentActivity] that owns this view. Required for fragment management
 *   and lifecycle-scoped coroutines.
 * @param listener Optional listener for reader events. Can also be set after construction.
 */
@SuppressLint("ViewConstructor", "ResourceType")
class EpubView(
    context: Context,
    val activity: FragmentActivity,
    var listener: EpubViewListener? = null
) : FrameLayout(context), EpubNavigatorFragment.Listener, DecorableNavigator.Listener {

    // Propagate layout through the view tree so ViewPager and WebViews size correctly.
    // See: https://github.com/readium/kotlin-toolkit/discussions/737
    override fun requestLayout() {
        super.requestLayout()
        post {
            measureAndLayoutRecursively(this)
        }
    }

    private fun measureAndLayoutRecursively(view: android.view.View) {
        view.forceLayout()
        view.measure(
            MeasureSpec.makeMeasureSpec(view.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(view.height, MeasureSpec.EXACTLY)
        )
        view.layout(view.left, view.top, view.right, view.bottom)
        (view as? android.view.ViewGroup)?.let { vg ->
            for (i in 0 until vg.childCount) {
                measureAndLayoutRecursively(vg.getChildAt(i))
            }
        }
    }

    var navigator: EpubNavigatorFragment? = null

    var locationEmitter: Job? = null

    private var changingResource = false

    var pendingProps: Props = Props(
        bookUuid = null,
        locator = null,
        isPlaying = null,
        highlights = null,
        bookmarks = null,
        readaloudColor = null,
        customFonts = null,
        foreground = null,
        background = null,
        fontFamily = null,
        lineHeight = null,
        paragraphSpacing = null,
        fontSize = null,
        textAlign = null,
    )
    var props: FinalizedProps? = null

    fun finalizeProps() {
        val oldProps = props

        val finalProps =
            FinalizedProps(
                bookUuid = pendingProps.bookUuid!!,
                locator = pendingProps.locator,
                isPlaying = pendingProps.isPlaying ?: oldProps?.isPlaying ?: false,
                highlights = pendingProps.highlights ?: oldProps?.highlights ?: listOf(),
                bookmarks = pendingProps.bookmarks ?: oldProps?.bookmarks ?: listOf(),
                readaloudColor = pendingProps.readaloudColor
                    ?: oldProps?.readaloudColor ?: 0xffffff00.toInt(),
                customFonts = pendingProps.customFonts ?: oldProps?.customFonts ?: listOf(),
                foreground = pendingProps.foreground
                    ?: oldProps?.foreground ?: "#111111".toColorInt(),
                background = pendingProps.background
                    ?: oldProps?.background ?: "#FFFFFF".toColorInt(),
                fontFamily = pendingProps.fontFamily
                    ?: oldProps?.fontFamily ?: FontFamily("Literata"),
                lineHeight = pendingProps.lineHeight ?: oldProps?.lineHeight ?: 1.4,
                paragraphSpacing = pendingProps.paragraphSpacing
                    ?: oldProps?.paragraphSpacing ?: 0.5,
                fontSize = pendingProps.fontSize ?: oldProps?.fontSize ?: 1.0,
                textAlign = pendingProps.textAlign ?: oldProps?.textAlign ?: TextAlign.JUSTIFY,
            )

        props = finalProps

        if (finalProps.bookUuid != oldProps?.bookUuid || finalProps.customFonts != oldProps.customFonts) {
            destroyNavigator()
            initializeNavigator()
        }

        if (finalProps.locator != navigator?.currentLocator && finalProps.locator != null) {
            go(finalProps.locator!!)
        }

        if (finalProps.isPlaying && finalProps.locator != null) {
            highlightFragment(finalProps.locator!!)
        } else {
            clearHighlightFragment()
        }

        if (finalProps.highlights != oldProps?.highlights) {
            decorateHighlights()
        }

        if (finalProps.bookmarks != oldProps?.bookmarks && finalProps.locator != null) {
            activity.lifecycleScope.launch { findOnPage(finalProps.locator!!) }
        }

        if (finalProps.readaloudColor != oldProps?.readaloudColor && finalProps.locator != null) {
            clearHighlightFragment()
            highlightFragment(finalProps.locator!!)
        }

        navigator?.submitPreferences(
            EpubPreferences(
                backgroundColor = org.readium.r2.navigator.preferences.Color(props!!.background),
                fontFamily = props!!.fontFamily,
                fontSize = props!!.fontSize,
                lineHeight = props!!.lineHeight,
                paragraphSpacing = props!!.paragraphSpacing,
                textAlign = props!!.textAlign,
                textColor = org.readium.r2.navigator.preferences.Color(props!!.foreground),
            )
        )
    }

    fun initializeNavigator() {
        val publication = BookService.getPublication(props!!.bookUuid) ?: return

        val fragmentTag = resources.getString(R.string.epub_fragment_tag)

        val epubFragment = EpubFragment(publication, this)

        activity.supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            add(epubFragment, fragmentTag)
        }

        addView(epubFragment.view)

        navigator = epubFragment.navigator

        decorateHighlights()

        navigator?.addDecorationListener("highlights", this)

        navigator?.addInputListener(
            DirectionalNavigationAdapter(
                navigator!!,
                animatedTransition = true,
            )
        )

        navigator?.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                this@EpubView.listener?.onMiddleTouch()
                return true
            }
        })

        locationEmitter = activity.lifecycleScope.launch {
            navigator?.currentLocator?.collect {
                onLocatorChanged(it)
            }
            emitCurrentLocator()
        }
    }

    fun destroyNavigator() {
        val navigator = this.navigator ?: return
        activity.supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            remove(navigator.requireParentFragment())
        }

        removeView(navigator.view)

        this.navigator = null
    }

    private suspend fun emitCurrentLocator() {
        val currentLocator = navigator!!.currentLocator.value

        val result = props?.locator?.locations?.fragments?.firstOrNull()?.let {
            navigator?.evaluateJavascript(
                """
            (function() {
                const element = document.getElementById("$it")
                return storyteller.isEntirelyOnScreen(element);
            })();
            """.trimIndent()
            )
        } ?: props?.locator?.locations?.progression?.let {
            navigator?.evaluateJavascript(
                """
            (function() {
                const width = Android.getViewportWidth();
                const pageWidth = width / window.devicePixelRatio;

                function snapOffset(offset) {
                    const value = offset + 1;
                    return value - (value % pageWidth);
                }

                const documentWidth = document.scrollingElement.scrollWidth;
                const currentPageStart = snapOffset(documentWidth * ${currentLocator.locations.progression});
                const currentPageEnd = currentPageStart + pageWidth;
                return $it * documentWidth >= currentPageStart &&
                    $it * documentWidth < currentPageEnd;
            })();
            """.trimIndent()
            )
        }

        val isPropLocatorOnPage = result?.let { Json.decodeFromString<Boolean?>(it) } ?: false

        val found = navigator!!.firstVisibleElementLocator()
        if (found == null) {
            // If the locator specified by the prop is still on the page, don't emit
            // a change event. We haven't actually changed the page.
            if (isPropLocatorOnPage) return
            listener?.onLocatorChange(currentLocator)
            return
        }

        val merged = currentLocator.copy(
            locations = currentLocator.locations.copy(
                fragments = found.locations.fragments,
                otherLocations = found.locations.otherLocations,
            ),
        )

        // If the locator specified by the prop is still on the page,
        // we still need to emit if we're adding fragments that we didn't
        // have initially
        if (isPropLocatorOnPage && (props?.locator?.locations?.fragments?.size ?: 0) > 0) {
            return
        }

        listener?.onLocatorChange(merged)
    }

    fun go(locator: Locator) {
        if (locator.href != navigator?.currentLocator?.value?.href) {
            changingResource = true
        }
        navigator!!.go(locator, true)
    }

    override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
        val rect = event.rect ?: return false
        val x = ceil(rect.centerX() / this.resources.displayMetrics.density).toInt()
        val y = ceil(rect.top / this.resources.displayMetrics.density).toInt() - 16
        listener?.onHighlightTap(event.decoration.id, x, y)
        return true
    }

    fun decorateHighlights() {
        val decorations = props!!.highlights.map {
            val style = Decoration.Style.Highlight(it.color, isActive = true)
            return@map Decoration(
                id = it.id,
                locator = it.locator,
                style = style
            )
        }

        activity.lifecycleScope.launch {
            navigator?.applyDecorations(decorations, group = "highlights")
        }
    }

    fun highlightFragment(locator: Locator) {
        val id = locator.locations.fragments.firstOrNull() ?: return

        val overlayHighlight = Decoration.Style.Highlight(props!!.readaloudColor, isActive = true)
        val decoration = Decoration(id, locator, overlayHighlight)

        activity.lifecycleScope.launch {
            navigator?.applyDecorations(listOf(decoration), "overlay")
        }
    }

    fun clearHighlightFragment() {
        activity.lifecycleScope.launch {
            navigator?.applyDecorations(listOf(), "overlay")
        }
    }

    suspend fun findOnPage(locator: Locator) {
        val epubNav = navigator ?: return
        val currentProgression = locator.locations.progression ?: return

        val joinedProgressions =
            props!!.bookmarks
                .filter { it.href == locator.href }
                .mapNotNull { it.locations.progression }
                .joinToString { it.toString() }

        val jsProgressionsArray = "[${joinedProgressions}]"

        val result = epubNav.evaluateJavascript(
            """
            (function() {
                const maxScreenX = window.orientation === 0 || window.orientation == 180
                        ? screen.width
                        : screen.height;

                function snapOffset(offset) {
                    const value = offset + 1;

                    return value - (value % maxScreenX);
                }

                const documentWidth = document.scrollingElement.scrollWidth;
                const currentPageStart = snapOffset(documentWidth * ${currentProgression});
                const currentPageEnd = currentPageStart + maxScreenX;
                return ${jsProgressionsArray}.filter((progression) =>
                    progression * documentWidth >= currentPageStart &&
                    progression * documentWidth < currentPageEnd
                );
            })();
            """.trimIndent()
        ) ?: run {
            listener?.onBookmarksActivate(listOf())
            return
        }

        val parsed = Json.decodeFromString<List<Double>>(result)
        val found = props!!.bookmarks.filter {
            val progression = it.locations.progression ?: return@filter false
            return@filter parsed.contains(progression)
        }

        listener?.onBookmarksActivate(found)
    }

    fun setupUserScript(): EpubView {
        activity.lifecycleScope.launch {
            val locator = props!!.locator ?: return@launch
            val fragments =
                BookService.getFragments(props!!.bookUuid, locator)

            val joinedFragments = fragments.joinToString { "\"${it.fragmentId}\"" }
            val jsFragmentsArray = "[${joinedFragments}]"

            navigator?.evaluateJavascript(
                """
                globalThis.storyteller = {};
                storyteller.doubleClickTimeout = null;
                storyteller.touchMoved = false;

                storyteller.touchStartHandler = (event) => {
                    storyteller.touchMoved = false;
                }

                storyteller.touchMoveHandler = (event) => {
                    storyteller.touchMoved = true;
                }

                storyteller.touchEndHandler = (event) => {
                    if (storyteller.touchMoved || !document.getSelection().isCollapsed || event.changedTouches.length !== 1) return;

                    event.bubbles = true
                    event.clientX = event.changedTouches[0].clientX
                    event.clientY = event.changedTouches[0].clientY
                    const clone = new MouseEvent('click', event);
                    event.stopImmediatePropagation();
                    event.preventDefault();

                    if (storyteller.doubleClickTimeout) {
                        clearTimeout(storyteller.doubleClickTimeout);
                        storyteller.doubleClickTimeout = null;
                        storytellerAPI.handleDoubleTap(event.currentTarget.id);
                        return
                    }

                    const element = event.currentTarget;

                    storyteller.doubleClickTimeout = setTimeout(() => {
                        storyteller.doubleClickTimeout = null;
                        element.parentElement.dispatchEvent(clone);
                    }, 350);
                }

                storyteller.observer = new IntersectionObserver((entries) => {
                    entries.forEach((entry) => {
                        if (entry.isIntersecting) {
                            entry.target.addEventListener('touchstart', storyteller.touchStartHandler)
                            entry.target.addEventListener('touchmove', storyteller.touchMoveHandler)
                            entry.target.addEventListener('touchend', storyteller.touchEndHandler)
                        } else {
                            entry.target.removeEventListener('touchstart', storyteller.touchStartHandler)
                            entry.target.removeEventListener('touchmove', storyteller.touchMoveHandler)
                            entry.target.removeEventListener('touchend', storyteller.touchEndHandler)
                        }
                    })
                }, {
                    threshold: [0],
                })

                document.addEventListener('selectionchange', () => {
                    if (document.getSelection().isCollapsed) {
                        storytellerAPI.handleSelectionCleared();
                    }
                });

                storyteller.isEntirelyOnScreen = function isEntirelyOnScreen(element) {
                    const rects = element.getClientRects()
                    return Array.from(rects).every((rect) => {
                        const isVerticallyWithin = rect.bottom >= 0 && rect.top <= window.innerHeight;
                        const isHorizontallyWithin = rect.right >= 0 && rect.left <= window.innerWidth;
                        return isVerticallyWithin && isHorizontallyWithin;
                    });
                }

                readium.findFirstVisibleLocator = function findFirstVisibleLocator() {
                    let firstVisibleFragmentId = null;

                    for (const fragmentId of storyteller.fragmentIds) {
                        const element = document.getElementById(fragmentId);
                        if (!element) continue;
                        if (storyteller.isEntirelyOnScreen(element)) {
                            firstVisibleFragmentId = fragmentId
                            break
                        }
                    }

                    if (firstVisibleFragmentId === null) return null;

                    return {
                        href: "#",
                        type: "application/xhtml+xml",
                        locations: {
                            cssSelector: "#" + firstVisibleFragmentId,
                            fragments: [firstVisibleFragmentId]
                        },
                        text: {
                            highlight: document.getElementById(firstVisibleFragmentId).textContent,
                        },
                    };
                }

                storyteller.fragmentIds = $jsFragmentsArray;
                storyteller.fragmentIds.map((id) => document.getElementById(id)).forEach((element) => {
                    storyteller.observer.observe(element)
                })
                """.trimIndent()
            )
        }

        return this
    }

    @JavascriptInterface
    fun handleDoubleTap(fragment: String) {
        val bookService = BookService
        val currentLocator = navigator?.currentLocator?.value ?: return
        activity.lifecycleScope.launch {
            val locator =
                bookService.buildFragmentLocator(props!!.bookUuid, currentLocator.href, fragment)

            listener?.onDoubleTouch(locator)
        }
    }

    @JavascriptInterface
    fun handleSelectionCleared() {
        listener?.onSelectionCleared()
    }

    private suspend fun onLocatorChanged(locator: Locator) {
        findOnPage(locator)

        if (locator.href != props!!.locator?.href || changingResource) {
            changingResource = false

            val fragments = BookService.getFragments(props!!.bookUuid, locator)

            val joinedFragments = fragments.joinToString { "\"${it.fragmentId}\"" }
            val jsFragmentsArray = "[${joinedFragments}]"

            navigator?.evaluateJavascript(
                """
                storyteller.fragmentIds = $jsFragmentsArray;
                storyteller.fragmentIds.map((id) => document.getElementById(id)).forEach((element) => {
                    storyteller.observer.observe(element)
                })
            """.trimIndent()
            )
            emitCurrentLocator()
        } else {
            emitCurrentLocator()
        }
    }

    @ExperimentalReadiumApi
    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        TODO("Not yet implemented")
    }
}
