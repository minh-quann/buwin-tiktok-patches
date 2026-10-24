# Kyant0 / MEBIECO LiquidBottomTabs integration

Upstream: https://github.com/Kyant0/AndroidLiquidGlass
Pinned commit: `65ab177e90e5c1d8c62e70cf7755841982da65f6` (Backdrop 2.0.1).
License: Apache-2.0; full license: LICENSE in this directory.

The unmodified catalog reference is retained in
`extensions/tiktok/src/glass/kotlin/app/morphe/extension/tiktok/glass/upstream/`:
LiquidBottomTabs.kt, LiquidBottomTab.kt, DampedDragAnimation.kt,
InteractiveHighlight.kt, DragGestureInspector.kt. Only catalog package/import names
are relocated; component/effect/gesture/animation bodies are unchanged.
AndroidFrame.kt supplies the original Android coroutine frame operation.
Backdrop 2.0.1, Shapes 1.2.1 and Capsule 2.1.1 are bundled from their original AARs.
The active component is the user's customized MEBIECO version copied from
`/home/quan/MEBIECO/mobile/node_modules/expo-liquid-glass-native/android/src/main/java/expo/modules/liquidglassnative/`
into `extensions/tiktok/src/glass/kotlin/app/morphe/extension/tiktok/glass/mebieco/`.
Its five component/utility files retain their original effects/gestures, with an approved
blur-strength and active-content tint parameters plus package/import relocation and attribution; `mebieco.sha256.json` records the source hashes before relocation.
NativeLiquidTabs.kt uses this component, including its touch overlay, lens punch-out,
ContinuousCapsule, 56dp bar, 48dp pill and 21dp lens. The adapter sets both blur passes to 2dp.
The adapter supplies white ink on feed/dark pages and black ink on light native pages;
MEBIECO's orange default is not used. Foreground fade/pixel shift applies to the Compose
tab columns, while native copies stay transparent through host rebinds and fade resets.
The adapter colors monochrome icons/text individually and disables the optional whole-row
active tint. Otherwise the white tint also replaces the black plus and cyan/red edges of
TikTok's multicolor create bitmap as the lens crosses it. The original tint remains the
component's default; effects and gestures are unchanged.
The capsule, side margins, icons and labels keep MEBIECO's sizes. At the user's request,
the adapter reduces only the bottom gap from 24dp to 8dp: its native frame is now 72dp
(8dp top + 56dp capsule + 8dp bottom), lowering the capsule and native caption/seek reserve
by 16dp together. Side margins remain 16dp, icons 20dp and labels 12sp.
Other icon slots use MEBIECO's 20dp size. The native multicolor create button has its
own 48x34dp slot, as requested, with transparent artwork margins trimmed once.
The capsule, pill, press scales and labels match the MEBIECO component; only the
adapter's bottom gap has been reduced as described above.

The component is an attached child of TikTok's original bottom-tabs component.
Native controls retain their bounds, click/touch listeners and navigation actions;
pointer events starting inside the capsule are claimed at the native mask dispatcher,
before TikTok's overlapping seek target. Compose owns the complete DOWN/MOVE/UP/CANCEL
stream, even outside the capsule; its original release callback invokes a native tab action
exactly once. Native accessibility actions remain available. Other video/seek gestures keep
the original native dispatcher. Icons/labels come from native views.
The original/customized component supplies its capsule, moving pill, springs, highlights,
shadows, chromatic refraction and drag behavior.

The local builder compiles the component with the Compose compiler, generates scoped
resource tags/strings, then R8 shrinks and repackages its Compose/Kotlin/AndroidX runtime
into a private namespace. It must not bind to TikTok's renamed Compose classes.
Resource strings are served by the embedded component's Context; no Activity overlay,
global theme/resource replacement or player surface/listener replacement is installed.
Use scripts/build-local-native-bottom-bar.py to rebuild both hooks and extension.

The native VideoSeekBarMaskView dispatchDraw/drawChild hooks share its ORIGINAL content
pass through NativeGlassScene. Native content is recorded once into a RenderNode and drawn
normally; only the tabs are excluded and drawn afterwards through the parent's drawChild.
The original Kyant layerBackdrop/effects reuse that GPU display list, rather than traversing
and drawing the native page a second time. No screenshot or TextureView.getBitmap is used.
A region-culled recorder remains a fallback for views without the main native scene hook.
Native light/dark background colors are inspected only on layout/tab changes.
The Compose child owns the full 72dp frame; native child/padding clipping is disabled only
on the tabs component so the pill's press growth and shadows have room.

Scrolling wakes Backdrop on the native animation frame. Dirty native content can wake it;
redrawing only the glass cannot create an invalidate feedback loop. Static non-Home pages
have no periodic capture loop. Texture frames intersecting the capsule can invalidate draw
at up to 30 Hz without recomposing tabs or recording the native view hierarchy again.
A visible SurfaceView keeps the original native dispatchDraw path: its compositor-position
RenderNode must never be replayed through a refracting layer. Detection is cached until
layout/tab changes. SurfaceView uses asynchronous PixelCopy of only
the strip beneath the bar, at most 192px wide and five times a second, only on Home.
Old bitmaps are reclaimed by Android rather than recycled underneath RenderThread.
The original MEBIECO effects retain lens 21dp and their spring/gesture behavior;
blurRadiusDp defaults to the reference 4dp and is set to 2dp by the TikTok adapter.

NativeVideoAmbient paints a cached native-cover palette only on the selected Home tab,
and only if the playing picture reaches the bar or ends within 8dp above it. A distant
letterboxed video leaves the black gap untouched. Other tabs receive a transparent
native tabs frame with the floating capsule; no ambient background is drawn outside it.
Actual picture pixels are clipped out. Palette sampling occurs once from the already
softened native cover; there is no recurring video screenshot for gradient colors.
Native cover blur uses separable sliding windows rather than 49 neighbors per output pixel.
The native main-pager strip is collapsed during measure so native page content/background
can continue behind the floating bar. Caption/rail/seek reserve the native host frame.
The feed picture's fill viewport ends at that same seek/bar boundary, calculated from
stable parent dimensions and measured bar height. Counting the entire parent including
the glass frame made 16:9 exceed the 16% cap on the 1320x2868 Honor, producing a 260px
top gap until a gesture changed layout. Hidden tabs during pinch retain the held boundary.
Smart crop still limits source loss to 16%; native cover fill is rechecked when the viewport changes.
Playback behavior and final visual fidelity still require an installed device check.

Non-Home tabs also collapse the native page footer slots wr4/ch4 during measure. These
opaque 160px slots otherwise cover the lower half of the capsule and the space below it
even with a transparent tabs background. Home restores those slots and keeps its native
black area and edge-conditioned ambient gradient. Slot height/alpha is owned/restorable;
no Activity background, system-bar geometry or content scale is changed.

Friends' recommendation PowerList (hj6) also carries a separate 58dp bottom margin
(190px on the inspected emulator). Non-Home mode clears that margin so the list itself
continues behind the bar; it preserves native top margin and scroll padding. Owned footer
slots and margins are rechecked after late native layout writes, without depending only on
isLayoutRequested. An empty transient tab selection keeps the last confirmed mode; explicit
Home selection restores its original slots/margin and retains its black area/gradient.
