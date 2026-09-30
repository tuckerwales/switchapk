# Decision log

Short records of significant decisions. Add new entries at the bottom;
do not rewrite old ones (supersede them with a new entry instead).

### D1. Interpret Dalvik bytecode directly (no ART, no dex-to-native AOT)
Rationale: portable, small, debuggable; the Switch forbids easy runtime
code generation for homebrew. Consequence: performance work goes into the
interpreter (quickening, inline caches) rather than a JIT.

### D2. Own libcore and framework instead of porting AOSP wholesale
Rationale: AOSP's framework assumes Binder, system services, Skia, HWUI,
ICU and a multi-process OS; porting that surface is larger than
reimplementing the app-facing API. We port algorithms from AOSP where
behaviour must match exactly. Consequence: API signatures must be kept in
sync by hand (see CONVENTIONS.md).

### D3. Auto-stub missing framework members
Rationale: lets real apps run while coverage grows. Consequence: silent
no-ops; always grep logs for `STUB:`.

### D4. GIL with real threads
Rationale: simple correctness for a single-core-performance interpreter;
matches Java memory model trivially. Consequence: natives must release the
GIL around blocking work; JNI code runs without the GIL.

### D5. Non-moving mark/sweep GC with conservative C stack scanning
Rationale: natives and JNI can hold raw pointers; no handle discipline
needed in C. Consequence: fragmentation and pause times are WS12 concerns.

### D6. Software rendering for the View system
Rationale: HWUI/Skia port is out of scope; a small C rasterizer is enough
for UI at 720p/1080p. GL apps use real GLES (mesa) directly. Consequence:
large animated UIs may be CPU bound; dirty-rect rendering is a later
optimisation.

### D7. Real framework resources from the SDK android.jar
Rationale: apps' themes inherit from `@android:style/Theme.*` and use
framework drawables/layouts (for example `simple_list_item_1`); using the
real compiled table gives Material visuals and correct attribute
resolution for free. Consequence: framework-res.apk is generated at build
time (Apache 2.0 content, not committed); widgets must read attributes
the AOSP way.

### D8. Report SDK_INT 29, implement API 35 signatures
Rationale: API 29 behaviour avoids newer restrictions/paths (scoped
storage, notification permissions, predictive back) while API 35
signatures let newer apps link.

### D9. 240 dpi at 720p (360 dpi at 1080p)
Rationale: phone-like 853x480 dp layout; text legible on the 6.2" screen
and on a TV. Portrait-only apps are letterboxed with density lowered to
keep at least 320 dp width.

### D10. Bitmaps store unpremultiplied ARGB int[] in Java
Rationale: GC-managed memory, no finalizers needed, `getPixel` is a plain
array read; the renderer consumes the same format. Consequence: GL uploads
and `copyPixelsToBuffer` convert.

### D11. Single process, single task stack
Rationale: apps rarely depend on multiple processes; in-process services,
providers and broadcasts are enough.

### D12. Toolchains fetched into build/toolchains, never committed
Rationale: large binaries and third-party licensing; reproducible with
`tools/fetch_toolchains.py`.
