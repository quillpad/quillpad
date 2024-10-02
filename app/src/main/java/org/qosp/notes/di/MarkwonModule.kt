package org.qosp.notes.di

import android.content.Context
import android.text.style.BackgroundColorSpan
import android.text.util.Linkify
import android.util.TypedValue
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.FragmentScoped
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.SpanFactory
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.handler.EmphasisEditHandler
import io.noties.markwon.editor.handler.StrongEmphasisEditHandler
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import io.noties.markwon.simple.ext.SimpleExtPlugin
import me.saket.bettermovementmethod.BetterLinkMovementMethod
import org.qosp.notes.R
import org.qosp.notes.data.sync.core.SyncManager
import org.qosp.notes.ui.editor.markdown.BlockQuoteHandler
import org.qosp.notes.ui.editor.markdown.CodeBlockHandler
import org.qosp.notes.ui.editor.markdown.CodeHandler
import org.qosp.notes.ui.editor.markdown.HeadingHandler
import org.qosp.notes.ui.editor.markdown.StrikethroughHandler
import org.qosp.notes.ui.utils.coil.CoilImagesPlugin
import org.qosp.notes.ui.utils.resolveAttribute

@Module
@InstallIn(FragmentComponent::class)
object MarkwonModule {

    @Provides
    @FragmentScoped
    fun provideMarkwon(@ActivityContext context: Context, syncManager: SyncManager): Markwon {
        return Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create(Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS))
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(MovementMethodPlugin.create(BetterLinkMovementMethod.getInstance()))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver(LinkResolverDef())
                }
            })
            .usePlugin(SimpleExtPlugin.create { plugin: SimpleExtPlugin ->
                plugin
                    .addExtension(
                        2,
                        '=',
                        SpanFactory { _, _ ->
                            val typedValue = TypedValue()
                            context.theme.resolveAttribute(R.attr.colorNoteTextHighlight, typedValue, true)
                            val color = typedValue.data
                            return@SpanFactory BackgroundColorSpan(color)
                        })
            })
            .usePlugin(CoilImagesPlugin.create(context, syncManager))
            .apply {
                val mainColor = context.resolveAttribute(R.attr.colorMarkdownTask) ?: return@apply
                val backgroundColor = context.resolveAttribute(R.attr.colorBackground) ?: return@apply
                usePlugin(TaskListPlugin.create(mainColor, mainColor, backgroundColor))
            }
            .build()
    }

    @Provides
    @FragmentScoped
    fun provideMarkwonEditor(markwon: Markwon): MarkwonEditor {
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
