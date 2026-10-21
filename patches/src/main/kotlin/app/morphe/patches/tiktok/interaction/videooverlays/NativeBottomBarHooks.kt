/*
 * Copyright 2026 Buwin contributors
 * https://github.com/minh-quann/buwin-tiktok-patches
 */
package app.morphe.patches.tiktok.interaction.videooverlays

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.addInstructions
import app.morphe.util.addInstructionsWithLabels
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val NATIVE_BOTTOM_BAR = "Lapp/morphe/extension/tiktok/feed/NativeBottomBar;"
private const val BOTTOM_ASSEM = "Lcom/ss/android/ugc/aweme/main/assems/mainfragment/BottomTabAssemBase;"
private val mainInflaters = listOf(
    "Lcom/by/andInflater/homepage_common_fragment_main_page;",
    "Lcom/by/andInflater/homepage_common_fragment_main_page_opt;",
)

/**
 * Discover the ORIGINAL bottom component through the controller's resource and both native
 * inflaters, rather than pinning an obfuscated class (05pn on 47.4.3). It must be the same
 * LinearLayout subclass in both inflaters and must still inherit these two methods unchanged.
 */
internal fun BytecodePatchContext.resolveNativeBottomBar(): () -> Unit {
    val getter = classDefBy(BOTTOM_ASSEM).methods.single {
        it.name == "wp" && it.parameterTypes.isEmpty() && it.returnType == "Landroid/view/ViewGroup;"
    }
    val ids = getter.implementation!!.instructions.filterIsInstance<NarrowLiteralInstruction>()
        .map { it.narrowLiteral }.filter { (it ushr 24) == 0x7f }.distinct()
    check(ids.size == 1) { "Native bottom bar: controller no longer identifies one bottom-tabs resource" }
    val barId = ids.single()
    val owners = mainInflaters.map { inflater ->
        val factories = classDefBy(inflater).methods.filter { method ->
            method.implementation?.instructions?.any {
                (it as? NarrowLiteralInstruction)?.narrowLiteral == barId
            } == true
        }
        check(factories.size == 1) { "Native bottom bar: $inflater has no unique bottom-tabs factory" }
        val code = factories.single().implementation!!.instructions.toList()
        val sites = code.indices.filter { (code[it] as? NarrowLiteralInstruction)?.narrowLiteral == barId }
        check(sites.size == 1) { "Native bottom bar: bottom-tabs resource is ambiguous in $inflater" }
        val index = sites.single()
        val allocation = (maxOf(0, index - 6) until index).lastOrNull { code[it].opcode == Opcode.NEW_INSTANCE }
        check(allocation != null) { "Native bottom bar: no component allocation before its resource" }
        checkNotNull(code[allocation].getReference<TypeReference>()).type
    }.distinct()
    check(owners.size == 1) { "Native bottom bar: the native inflaters disagree about the component" }
    val owner = owners.single()
    val component = classDefBy(owner)
    val parent = checkNotNull(component.superclass)
    check(classDefBy(parent).superclass == "Landroid/widget/LinearLayout;") {
        "Native bottom bar: component no longer extends the expected LinearLayout base"
    }
    check(component.methods.none { it.name in setOf("onMeasure", "dispatchDraw", "onApplyWindowInsets", "onAttachedToWindow", "onDetachedFromWindow", "onPreDraw") }) {
        "Native bottom bar: component already implements measurement/drawing; review its new behavior"
    }
    val measure = nativeOverride(owner, "onMeasure", listOf("I", "I"), 4, """
        invoke-static { p0, p2 }, $NATIVE_BOTTOM_BAR->heightSpec(Landroid/view/View;I)I
        move-result p2
        invoke-super { p0, p1, p2 }, $parent->onMeasure(II)V
        return-void
    """)
    val draw = nativeOverride(owner, "dispatchDraw", listOf("Landroid/graphics/Canvas;"), 2, """
        invoke-static { p0, p1 }, $NATIVE_BOTTOM_BAR->drawBackground(Landroid/view/View;Landroid/graphics/Canvas;)V
        invoke-super { p0, p1 }, $parent->dispatchDraw(Landroid/graphics/Canvas;)V
        return-void
    """)
    val insets = nativeOverride(owner, "onApplyWindowInsets", listOf("Landroid/view/WindowInsets;"), 2, """
        invoke-virtual { p0 }, Landroid/view/View;->requestLayout()V
        invoke-super { p0, p1 }, $parent->onApplyWindowInsets(Landroid/view/WindowInsets;)Landroid/view/WindowInsets;
        move-result-object p1
        return-object p1
    """, "Landroid/view/WindowInsets;", AccessFlags.PUBLIC.value)
    val listener = "Landroid/view/ViewTreeObserver\$OnPreDrawListener;"
    val attach = nativeOverride(owner, "onAttachedToWindow", emptyList(), 2, """
        invoke-super { p0 }, $parent->onAttachedToWindow()V
        invoke-virtual { p0 }, Landroid/view/View;->getViewTreeObserver()Landroid/view/ViewTreeObserver;
        move-result-object v0
        invoke-virtual { v0, p0 }, Landroid/view/ViewTreeObserver;->addOnPreDrawListener($listener)V
        return-void
    """)
    val detach = nativeOverride(owner, "onDetachedFromWindow", emptyList(), 2, """
        invoke-virtual { p0 }, Landroid/view/View;->getViewTreeObserver()Landroid/view/ViewTreeObserver;
        move-result-object v0
        invoke-virtual { v0, p0 }, Landroid/view/ViewTreeObserver;->removeOnPreDrawListener($listener)V
        invoke-super { p0 }, $parent->onDetachedFromWindow()V
        return-void
    """)
    val preDraw = nativeOverride(owner, "onPreDraw", emptyList(), 2, """
        invoke-static { p0 }, $NATIVE_BOTTOM_BAR->prepareFrame(Landroid/view/View;)Z
        move-result v0
        return v0
    """, "Z", AccessFlags.PUBLIC.value)
    val cell = "Lcom/ss/android/ugc/aweme/feed/VideoViewCellRootView;"
    val mask = "Lcom/ss/android/ugc/aweme/feed/ui/seekbar/VideoSeekBarMaskView;"
    val measureHooks = listOf(cell to "measureCell", mask to "measureFeed").map { (type, hook) ->
        val definition = classDefBy(type)
        check(definition.methods.none { it.name == "onMeasure" }) {
            "Native feed geometry: $type now owns onMeasure; review before patching"
        }
        type to nativeOverride(type, "onMeasure", listOf("I", "I"), 3, """
            invoke-static { p0 }, $NATIVE_BOTTOM_BAR->$hook(Landroid/view/View;)${if (hook == "measureFeed") "Z" else "V"}
            invoke-super { p0, p1, p2 }, ${definition.superclass}->onMeasure(II)V
            return-void
        """)
    }
    // Set the crop policy where TikTok calculates its FIRST result, before the rendering
    // surface is sized. Prevent the smart/multi-container strategies from replacing it later.
    val operator = "Lcom/ss/android/ugc/feed/platform/cell/component/adaption/FeedVideoAdaptionParamsOperator;"
    val definition = classDefBy(operator)
    val scale = definition.methods.single { it.name == "getForceScaleType" && it.parameterTypes.isEmpty() }
    val scaleType = scale.returnType
    check(classDefBy(scaleType).fields.any { it.name == "CROP" && it.type == scaleType })
    check(definition.fields.any { it.name == "eventType" && it.type == "Ljava/lang/String;" })
    val policyHooks = listOf("getForceScaleType", "getContainerAvoidTopBar", "getEnableMultiContainer", "getEnableSmartCrop")
    policyHooks.forEach { name ->
        val method = definition.methods.single { it.name == name && it.parameterTypes.isEmpty() }
        check(method.implementation!!.registerCount >= 2) { "Native crop: $name has no local register" }
    }
    // 47.4.3 native keyboard roots/drawer, verified against the input XML and Honor hierarchy.
    val safeAreas = listOf("LX/1QL4;" to "measureDetailInput", "LX/0XNg;" to "measureChat",
        "LX/0FOq;" to "measureCommentSheet")
    safeAreas.forEach { (type, _) ->
        val view = classDefBy(type)
        check(view.superclass in listOf("Landroid/widget/LinearLayout;", "Landroid/widget/FrameLayout;"))
        check(view.methods.none { it.name == "onApplyWindowInsets" })
    }
    check(classDefBy("LX/1QL4;").methods.none { it.name == "onDetachedFromWindow" })
    val sheetType = "Lcom/google/android/material/bottomsheet/BottomSheetBehavior;"
    val sheetLayout = classDefBy(sheetType).methods.single {
        it.name == "onLayoutChild" && it.parameterTypes.size == 3
                && it.parameterTypes[1] == "Landroid/view/View;" && it.returnType == "Z"
    }
    val paramsType = "Lcom/ss/android/ugc/aweme/videoadaption/adaptionparams/VideoAdaptionParams;"
    val paramsInit = classDefBy(paramsType).methods.single { it.name == "<init>" }
    listOf("getVideoWidth", "getVideoHeight", "getParamsOperator").forEach { getter ->
        check(classDefBy(paramsType).methods.any { it.name == getter && it.parameterTypes.isEmpty() })
    }
    val strategyType = "LX/08Nx;"
    val strategy = classDefBy(strategyType).methods.single { it.name == "LIZLLL" && it.parameterTypes.size == 3
            && it.parameterTypes[1] == paramsType }
    val shrinkType = "Lcom/ss/android/ugc/feed/platform/cell/shrink/VideoShrinkComponent;"
    val shrinkMethods = listOf(listOf("I", "I"), listOf("J", "Z", "Z")).map { types ->
        classDefBy(shrinkType).methods.single { it.parameterTypes == types && it.returnType == "V" }.name
    }
    val liveType = "LX/0KOM;"
    check(classDefBy(liveType).superclass == "Landroid/view/SurfaceView;")
    val liveMeasure = classDefBy(liveType).methods.single { it.name == "onMeasure" && it.parameterTypes == listOf("I", "I") }
    check(liveMeasure.implementation!!.registerCount - 3 >= 5)
    val liveSize = classDefBy(liveType).methods.single { it.name == "setVideoSize" && it.parameterTypes == listOf("I", "I") }
    val videoFields = liveSize.implementation!!.instructions.filter { it.opcode == Opcode.IPUT }
        .mapNotNull { it.getReference<FieldReference>() }.filter { it.definingClass == liveType && it.type == "I" }
    check(videoFields.size == 2) { "LIVE preview no longer stores one width and one height" }
    // The native edge gesture hides the bottom tabs/enters clear mode while holding 2x.
    // Freeze before that UI event, and release only after the original geometry settles.
    val edgeType = "Lcom/ss/android/ugc/aweme/feed/longvideo/edgespeedup/EdgeSpeedupAssem;"
    val edge = classDefBy(edgeType)
    val holdHooks = listOf(
        listOf("F", "F") to ("ripple_shown_count" to "begin"),
        listOf("Z") to ("speedup_duration" to "end"),
    ).map { (parameters, keyAndHook) ->
        val method = edge.methods.single { method ->
            method.parameterTypes == parameters && method.returnType == "V" &&
                method.implementation?.instructions?.any {
                    it.getReference<StringReference>()?.string == keyAndHook.first
                } == true
        }
        val code = method.implementation!!.instructions.toList()
        val state = checkNotNull(code.first().getReference<FieldReference>())
        check(code.first().opcode == Opcode.IGET_BOOLEAN && state.definingClass == edgeType)
        val writes = code.indices.filter { code[it].opcode == Opcode.IPUT_BOOLEAN &&
                code[it].getReference<FieldReference>()?.toString() == state.toString() }
        check(writes.size == 1) { "Native speed hold: ambiguous active-state write" }
        Triple(method.name, writes.single() + 1, keyAndHook.second)
    }
    // 47.4.3's native pull-down speed-lock pill, independently from the video/widget layout.
    val guideType = "LX/10fn;"
    val guide = classDefBy(guideType)
    check(guide.superclass == "Landroid/widget/LinearLayout;" && "LX/1KtO;" in guide.interfaces
            && guide.methods.none { it.name == "onMeasure" }
            && guide.methods.any { it.parameterTypes == listOf("F", "Z") && it.returnType == "V" }) {
        "Native hold guide: review the pill component before adjusting its margins"
    }
    val guideMeasure = nativeOverride(guideType, "onMeasure", listOf("I", "I"), 3, """
        invoke-static { p0 }, Lapp/morphe/extension/tiktok/feed/HoldVideoViewport;->positionGuide(Landroid/view/View;)V
        invoke-super { p0, p1, p2 }, Landroid/widget/LinearLayout;->onMeasure(II)V
        return-void
    """)
    return {
        mutableClassDefBy(guideType).methods.add(guideMeasure)
        holdHooks.forEach { (name, index, hook) ->
            mutableClassDefBy(edgeType).methods.single { it.name == name }.addInstructions(index,
                "invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/tiktok/feed/HoldVideoViewport;->$hook(Ljava/lang/Object;)V")
        }
        val mutable = mutableClassDefBy(owner)
        mutable.interfaces.add(listener)
        listOf(measure, draw, insets, attach, detach, preDraw).forEach { mutable.methods.add(it) }
        measureHooks.forEach { (type, method) -> mutableClassDefBy(type).methods.add(method) }
        safeAreas.forEach { (type, hook) ->
            val view = mutableClassDefBy(type)
            val parentType = checkNotNull(view.superclass)
            val call = "invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/tiktok/feed/NativeSafeArea;->$hook(Landroid/view/View;)V"
            val existing = view.methods.singleOrNull { it.name == "onMeasure" && it.parameterTypes == listOf("I", "I") }
            if (existing != null) existing.addInstructions(0, call)
            else view.methods.add(nativeOverride(type, "onMeasure", listOf("I", "I"), 3, """
                $call
                invoke-super { p0, p1, p2 }, $parentType->onMeasure(II)V
                return-void
            """))
            view.methods.add(nativeOverride(type, "onApplyWindowInsets", listOf("Landroid/view/WindowInsets;"), 2, """
                invoke-virtual { p0 }, Landroid/view/View;->requestLayout()V
                invoke-super { p0, p1 }, $parentType->onApplyWindowInsets(Landroid/view/WindowInsets;)Landroid/view/WindowInsets;
                move-result-object p1
                return-object p1
            """, "Landroid/view/WindowInsets;", AccessFlags.PUBLIC.value))
        }
        mutableClassDefBy(sheetType).methods.single { it.name == sheetLayout.name && it.parameterTypes == sheetLayout.parameterTypes }
            .addInstructions(0, "invoke-static/range { p2 .. p2 }, Lapp/morphe/extension/tiktok/feed/NativeSafeArea;->measureGenericSheet(Landroid/view/View;)V")
        mutableClassDefBy("LX/1QL4;").methods.add(nativeOverride("LX/1QL4;", "onDetachedFromWindow", emptyList(), 1, """
            invoke-static { p0 }, Lapp/morphe/extension/tiktok/feed/NativeSafeArea;->releaseDetailInput(Landroid/view/View;)V
            invoke-super { p0 }, Landroid/widget/LinearLayout;->onDetachedFromWindow()V
            return-void
        """))
        mutableClassDefBy(paramsType).methods.single { it.name == paramsInit.name }.apply {
            implementation!!.instructions.toList().indices.filter { getInstruction(it).opcode == Opcode.RETURN_VOID }
                .reversed().forEach { addInstructions(it, "invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/tiktok/interaction/StatusBarVideoFill;->rememberParams(Ljava/lang/Object;)V") }
        }
        mutableClassDefBy(strategyType).methods.single { it.name == strategy.name && it.parameterTypes == strategy.parameterTypes }.apply {
            val code = implementation!!.instructions.toList()
            code.indices.filter { code[it].opcode == Opcode.RETURN_OBJECT }.reversed().forEach { index ->
                val register = (code[index] as OneRegisterInstruction).registerA
                check(implementation!!.registerCount <= 16)
                addInstructions(index, "invoke-static { v$register, p2 }, Lapp/morphe/extension/tiktok/interaction/StatusBarVideoFill;->rememberResult(Ljava/lang/Object;Ljava/lang/Object;)V")
            }
        }
        shrinkMethods.forEach { name ->
            mutableClassDefBy(shrinkType).methods.single { it.name == name && it.returnType == "V" }.apply {
                check(implementation!!.registerCount - 1 - parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 } >= 1)
                addInstructionsWithLabels(0, """
                    invoke-static {}, Lapp/morphe/extension/tiktok/playback/CommentShrink;->shouldDisableShrink()Z
                    move-result v0
                    if-eqz v0, :native_shrink_original
                    return-void
                """, ExternalLabel("native_shrink_original", getInstruction(0)))
            }
        }
        mutableClassDefBy(liveType).methods.single { it.name == "onMeasure" && it.parameterTypes == liveMeasure.parameterTypes }.apply {
            addInstructionsWithLabels(0, """
                move-object/from16 v4, p0
                iget v0, v4, $liveType->${videoFields[0].name}:I
                iget v1, v4, $liveType->${videoFields[1].name}:I
                invoke-static { v4, v0, v1 }, Lapp/morphe/extension/tiktok/interaction/NativeLivePreview;->measure(Landroid/view/View;II)J
                move-result-wide v0
                const-wide/16 v2, 0x0
                cmp-long v4, v0, v2
                if-lez v4, :native_live_original
                long-to-int v3, v0
                const/16 v2, 0x20
                ushr-long/2addr v0, v2
                long-to-int v2, v0
                move-object/from16 v4, p0
                invoke-virtual { v4, v2, v3 }, Landroid/view/View;->setMeasuredDimension(II)V
                return-void
            """, ExternalLabel("native_live_original", getInstruction(0)))
        }
        policyHooks.forEach { name ->
            val method = mutableClassDefBy(operator).methods.single { it.name == name && it.parameterTypes.isEmpty() }
            method.addInstructionsWithLabels(0, """
                invoke-static { p0 }, Lapp/morphe/extension/tiktok/interaction/StatusBarVideoFill;->useNativeCrop(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :native_crop_original
                ${if (name == "getForceScaleType") "sget-object v0, $scaleType->CROP:$scaleType\nreturn-object v0" else "const/4 v0, 0x0\nreturn v0"}
            """, ExternalLabel("native_crop_original", method.getInstruction(0)))
        }
    }
}

private fun nativeOverride(owner: String, name: String, parameters: List<String>, registers: Int,
                           code: String, result: String = "V",
                           flags: Int = AccessFlags.PROTECTED.value): MutableMethod = MutableMethod(ImmutableMethod(owner, name,
    parameters.map { ImmutableMethodParameter(it, null, null) }, result, flags,
    null, null, ImmutableMethodImplementation(registers, emptyList(), null, null))).also { it.addInstructions(code) }
