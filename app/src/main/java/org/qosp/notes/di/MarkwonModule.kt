package org.qosp.notes.di

import android.content.Context
import android.text.style.BackgroundColorSpan
import android.text.util.Linkify.EMAIL_ADDRESSES
import android.text.util.Linkify.WEB_URLS
import android.util.TypedValue
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.Markwon.builder
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.SpanFactory
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.handler.EmphasisEditHandler
import io.noties.markwon.editor.handler.StrongEmphasisEditHandler
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin.create
import io.noties.markwon.linkify.LinkifyPlugin.create
import io.noties.markwon.movement.MovementMethodPlugin.create
import io.noties.markwon.simple.ext.SimpleExtPlugin
import io.noties.markwon.simple.ext.SimpleExtPlugin.create
import me.saket.bettermovementmethod.BetterLinkMovementMethod.getInstance
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.qosp.notes.R.attr.colorBackground
import org.qosp.notes.R.attr.colorMarkdownTask
import org.qosp.notes.R.attr.colorNoteTextHighlight
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.ui.editor.markdown.BlockQuoteHandler
import org.qosp.notes.ui.editor.markdown.CodeBlockHandler
import org.qosp.notes.ui.editor.markdown.CodeHandler
import org.qosp.notes.ui.editor.markdown.HeadingHandler
import org.qosp.notes.ui.editor.markdown.StrikethroughHandler
import org.qosp.notes.ui.utils.coil.CoilImagesPlugin
import org.qosp.notes.ui.utils.resolveAttribute

@Module
class MarkwonModule {

    @Factory
    fun getMarkwon(context: Context, syncManager: SyncManager): Markwon = builder(context)
        .usePlugin(create(EMAIL_ADDRESSES or WEB_URLS))
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(create(getInstance()))
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver(LinkResolverDef())
            }
        })
        .usePlugin(create { plugin: SimpleExtPlugin ->
            plugin
                .addExtension(
                    2,
                    '=',
                    SpanFactory { _, _ ->
                        val typedValue = TypedValue()
                        context.theme.resolveAttribute(colorNoteTextHighlight, typedValue, true)
                        val color = typedValue.data
                        return@SpanFactory BackgroundColorSpan(color)
                    })
        })
        .usePlugin(CoilImagesPlugin.create(context, syncManager))
        .apply {
            val mainColor = context.resolveAttribute(colorMarkdownTask) ?: return@apply
            val backgroundColor = context.resolveAttribute(colorBackground) ?: return@apply
            usePlugin(create(mainColor, mainColor, backgroundColor))
        }
        .build()

    @Factory
    fun getMarkWonEditor(context: Context, markwon: Markwon): MarkwonEditor {
        return MarkwonEditor.builder(markwon)
            .useEditHandler(EmphasisEditHandler())
            .useEditHandler(StrongEmphasisEditHandler())
            .useEditHandler(CodeHandler())
            .useEditHandler(CodeBlockHandler())
            .useEditHandler(BlockQuoteHandler())
            .useEditHandler(StrikethroughHandler())
            .useEditHandler(HeadingHandler())
            .build()
    }

}
