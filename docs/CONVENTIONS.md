# Conventions

## Android API fidelity (most important rule)

- Every public class, method, field and constant we add in `android.*`
  must have exactly the name, parameter types, return type, static-ness
  and declaring class of the real API in android.jar (API 35 file). Check
  with `javap -cp build/toolchains/sdk/android.jar -public android.widget.TextView`.
  A mismatch does not fail loudly: the VM stubs the app's call and returns
  0/null (see ARCHITECTURE 4.2).
- Behaviour follows API 29 (`Build.VERSION.SDK_INT == 29`) unless an app
  targeting a newer SDK depends on newer behaviour.
- When in doubt, port the AOSP implementation's logic (Apache 2.0) rather
  than inventing behaviour. Keep AOSP names for fields that apps or
  AndroidX read by reflection (for example `View.mListenerInfo`,
  `TextView.mCursorDrawableRes`, `Paint.mNativePaint` equivalents where
  they matter); document such cases in comments.
- Hidden/internal helpers needed across packages may be public; add a
  one-line comment saying they are framework-internal.
- Constants must use the real values (resource ids from generated
  `android.R`, keycodes, flags, enum ordinals where apps persist them).
- Never throw for "not implemented" in paths that apps commonly hit; log
  once and do the closest safe thing. Throw only where Android throws.

## Java code

- Java 8 language level (compiled with `-source 8`), no Java 9+ APIs
  unless they exist in our libcore.
- The framework compiles against our libcore only; if you need a JDK
  class that libcore lacks, add it to libcore (and a VmTest case when it
  has behaviour worth checking).
- 4-space indent, 120-column lines, standard Java naming, AOSP-style
  field prefixes (`mField`, `sStatic`) in framework classes.
- Keep state and logic in Java; natives are for pixels, parsing compiled
  resources and platform I/O.
- No finalizers; memory for pixels/audio lives in Java arrays.

## C code

- C11 (gnu11), 4-space indent, 120 columns, `snake_case`, module prefixes
  (`vm_`, `gfx_`, `arsc_`, `platform_`, `sa_` for core utilities).
- Logging with `LOGD/LOGI/LOGW/LOGE` and a per-file `LOG_TAG`.
- Allocation through `sa_malloc/sa_calloc/sa_realloc/sa_strdup` (abort on
  OOM) except where failure is expected (large image buffers: plain
  malloc + check).
- Portable across host (Linux x86-64/AArch64, glibc) and Switch
  (devkitA64, newlib, libnx). Guard Switch-only code with `__SWITCH__`.
- Compile warning-free with the flags in the Makefile.

## Natives contract

- Declare with `NATIVE(fn)`; read arguments with `A_*` accessors (long and
  double take two slots); return with `R_*`.
- Register in a `static const NativeMethodReg g_regs[]` table and a
  `*_register()` function called from `vm_natives_init()`.
- Java fields read from C are part of the interface: list them in
  ARCHITECTURE.md and in a comment next to the Java field
  (`// read by native code`).
- Blocking work: `vm_gil_release(t)` / `vm_gil_acquire(t)` around it; no
  Java object access in between.
- Objects held beyond the native call (C globals, heap structures) need
  `vm_add_root` or JNI global refs.
- Throw with `vm_throw_new` and return immediately.

## Resources and assets

- Framework resources come only from `framework-res.apk` (generated).
  Never hand-write copies of framework layouts or styles in Java; use the
  resource ids from `android.R` and let the theme system resolve them.
- Default widget styles are taken from the theme (`textViewStyle`,
  `buttonStyle`, ...) like AOSP constructors do
  (`View(Context, AttributeSet, int defStyleAttr, int defStyleRes)`).

## Tests

- VM/libcore changes: add or extend a `tests/dex/*.java` program and run
  `tests/run_dex_test.sh` for it; output must equal OpenJDK's.
- Framework changes: add or extend a sample in `tests/apps/` with a
  script and golden screenshots (WS13 provides the runner).
- Renderer changes: run `tests/c/gfx_test.c` and inspect the PNG.
- Do not commit binaries except small golden PNGs.

## Git and collaboration

- Integration branch: `main`, which is also the repository default.
  Agents working in parallel should use their own branches and merge
  (not rebase shared history) into `main` when their build is green.
- One logical change per commit, imperative subject line, body explaining
  why. End commit messages with the attribution lines required by the
  session instructions. No model names in commits or code.
- Claim work in `docs/WORKSTREAMS.md` before starting; update PLAN.md
  checklists and SESSION_LOG.md when finishing.
- Do not create pull requests unless the user asks.
- No em dashes in docs, comments or messages (user preference).
