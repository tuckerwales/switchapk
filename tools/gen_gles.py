#!/usr/bin/env python3
"""Generates the android.opengl GLES bindings.

Java signatures come from the SDK android.jar (javap), so they match the
real API exactly; C prototypes come from the Khronos headers. Each Java
native is paired with its C function by walking both parameter lists:

  C scalar            <- one Java primitive (int, boolean, float, long, ...)
  C pointer           <- X[] array + int offset | java.nio.*Buffer | String
                         | int or long (an offset into a bound GL buffer)
  const GLubyte * ret -> String

Methods that do not fit (output strings with sizes, String[] inputs,
callbacks, mapped buffers) are implemented by hand in
src/android/android_gles_special.c; the generator reads that file's
registration table and emits a logging fallback for anything still
missing.

Outputs (committed, do not edit):
  java/framework/android/opengl/GLES{10,10Ext,11,11Ext,20,30,31,31Ext,32}.java
  src/android/gles_funcs.h        X-macro list of every GL entry point used
  src/android/android_gles_gen.c  natives and their registration table

Usage: python3 tools/gen_gles.py [android.jar] [include dir]
Defaults: build/toolchains/sdk/android.jar and /usr/include (needs the
Khronos GLES, GLES2 and GLES3 headers, e.g. from libgles-dev).
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build/toolchains/sdk/android.jar")
INC = sys.argv[2] if len(sys.argv) > 2 else "/usr/include"

# class -> (superclass or None, header files whose prototypes it may use)
CLASSES = [
    ("GLES10", None),
    ("GLES10Ext", None),
    ("GLES11", "GLES10"),
    ("GLES11Ext", None),
    ("GLES20", None),
    ("GLES30", "GLES20"),
    ("GLES31", "GLES30"),
    ("GLES31Ext", None),
    ("GLES32", "GLES31"),
]
HEADERS = ["GLES2/gl2.h", "GLES3/gl3.h", "GLES3/gl31.h", "GLES3/gl32.h", "GLES2/gl2ext.h",
           "GLES/gl.h", "GLES/glext.h"]

# C types: (C spelling in generated code, Java primitive codes accepted)
SCALARS = {
    "GLenum": ("uint32_t", "I"), "GLuint": ("uint32_t", "I"), "GLbitfield": ("uint32_t", "I"),
    "GLint": ("int32_t", "I"), "GLsizei": ("int32_t", "I"), "GLfixed": ("int32_t", "I"),
    "GLclampx": ("int32_t", "I"),
    "GLboolean": ("uint8_t", "Z"), "GLfloat": ("float", "F"), "GLclampf": ("float", "F"),
    "GLshort": ("int16_t", "S"), "GLushort": ("uint16_t", "S"),
    "GLbyte": ("int8_t", "B"), "GLubyte": ("uint8_t", "B"),
    "GLintptr": ("intptr_t", "IJ"), "GLsizeiptr": ("intptr_t", "IJ"),
    "GLint64": ("int64_t", "J"), "GLuint64": ("uint64_t", "J"),
    "GLsync": ("void *", "J"), "GLeglImageOES": ("void *", "L"),
}
ARRAY_ELEM = {"I": 4, "F": 4, "Z": 1, "J": 8, "B": 1, "S": 2, "C": 2}
BUFFERS = {"Ljava/nio/Buffer;", "Ljava/nio/IntBuffer;", "Ljava/nio/FloatBuffer;", "Ljava/nio/ShortBuffer;",
           "Ljava/nio/ByteBuffer;", "Ljava/nio/LongBuffer;", "Ljava/nio/CharBuffer;", "Ljava/nio/DoubleBuffer;"}
# entry points that may block: called with the GIL released
BLOCKING = {"glFinish", "glReadPixels", "glClientWaitSync", "glReadnPixels", "glReadnPixelsKHR"}

PROTO_RE = re.compile(r"^GL_API(?:CALL)?\s+(.+?)\s*GL_APIENTRY\s+(\w+)\s*\((.*?)\);", re.M | re.S)


def parse_headers():
    protos = {}
    for h in HEADERS:
        path = os.path.join(INC, h)
        text = open(path).read()
        for ret, name, params in PROTO_RE.findall(text):
            if name in protos:
                continue
            params = " ".join(params.split())
            plist = [] if params in ("", "void") else [p.strip() for p in params.split(",")]
            protos[name] = (" ".join(ret.split()), plist)
    return protos


def c_param(p):
    """Splits a C parameter into (base type, is_pointer, name, original spelling)."""
    m = re.match(r"^(.*?)(\w+)$", p)
    decl, name = m.group(1).strip(), m.group(2)
    ptr = "*" in decl
    base = decl.replace("const", "").replace("*", "").strip()
    return base, ptr, name, decl


def c_type(base, ptr, decl):
    if ptr:
        return "const void *" if decl.startswith("const") else "void *"
    if base in SCALARS:
        return SCALARS[base][0]
    if base == "void":
        return "void"
    raise KeyError(base)


def javap(cls):
    out = subprocess.run(["javap", "-cp", JAR, "-public", "-s", "-constants", "android.opengl." + cls],
                         capture_output=True, text=True, check=True).stdout
    lines = out.splitlines()
    consts, natives, others = [], [], []
    i = 0
    while i < len(lines):
        ln = lines[i].strip()
        desc = lines[i + 1].strip()[len("descriptor: "):] if i + 1 < len(lines) and "descriptor:" in lines[i + 1] else None
        if ln.startswith("public static final"):
            consts.append(ln)
        elif " static " in ln and "(" in ln:
            # natives, plus the public static wrappers android.jar declares non-native (glVertexPointer(Buffer), ...)
            name = re.search(r"(\w+)\(", ln).group(1)
            natives.append((name, desc, ln))
        elif "(" in ln and desc:
            others.append(ln)
        i += 2 if desc else 1
    return consts, natives, others


def split_desc(desc):
    """'(I[ILjava/nio/Buffer;)V' -> (['I', '[I', 'Ljava/nio/Buffer;'], 'V')"""
    params, i = [], 1
    while desc[i] != ")":
        j = i
        while desc[j] == "[":
            j += 1
        if desc[j] == "L":
            j = desc.index(";", j)
        params.append(desc[i:j + 1])
        i = j + 1
    return params, desc[i + 1:]


JTYPE = {"I": "int", "Z": "boolean", "F": "float", "J": "long", "B": "byte", "S": "short", "C": "char",
         "D": "double", "V": "void"}


def java_type(d):
    if d.startswith("["):
        return java_type(d[1:]) + "[]"
    if d.startswith("L"):
        return d[1:-1].replace("/", ".").replace("java.lang.", "").replace("$", ".")
    return JTYPE[d]


def match(jparams, cparams):
    """Pairs Java descriptors with C params. Returns a plan list or None."""
    plan, j = [], 0
    for p in cparams:
        base, ptr, name, decl = c_param(p)
        if j >= len(jparams):
            return None
        jd = jparams[j]
        if not ptr and base in SCALARS and SCALARS[base][1] != "L":
            if len(jd) == 1 and jd in SCALARS[base][1]:
                plan.append(("scalar", name, jd, c_type(base, ptr, decl)))
                j += 1
                continue
            if base == "GLsync" and jd == "J":
                plan.append(("scalar", name, jd, "void *"))
                j += 1
                continue
            return None
        if not ptr and base not in SCALARS:
            return None
        # pointer (or GLeglImageOES, an opaque pointer typedef)
        ct = c_type(base, ptr, decl) if ptr else "void *"
        if "**" in decl.replace(" ", "") or (decl.count("*") > 1):
            return None
        if jd.startswith("[") and len(jd) == 2 and j + 1 < len(jparams) and jparams[j + 1] == "I":
            plan.append(("array", name, jd[1], ct))
            j += 2
        elif jd in BUFFERS:
            plan.append(("buffer", name, jd, ct))
            j += 1
        elif jd == "Ljava/lang/String;" and base in ("GLchar", "char") and decl.startswith("const"):
            plan.append(("string", name, jd, ct))
            j += 1
        elif jd in ("I", "J") and base == "void":
            plan.append(("offset", name, jd, ct))
            j += 1
        else:
            return None
    if j != len(jparams):
        return None
    return plan


def arg_reader(jd, slot):
    return {"I": "A_INT", "Z": "A_BOOL", "F": "A_FLOAT", "J": "A_LONG", "B": "A_INT", "S": "A_INT",
            "C": "A_INT"}[jd] + "(%d)" % slot


def slots(jd):
    return 2 if jd in ("J", "D") else 1


def special_regs():
    path = os.path.join(ROOT, "src/android/android_gles_special.c")
    if not os.path.exists(path):
        return set()
    text = open(path).read()
    return set(re.findall(r'\{"Landroid/opengl/(\w+);",\s*"(\w+)",\s*"([^"]+)"', text))


GL_IFACES = [("GL", []), ("GL10", ["GL"]), ("GL10Ext", ["GL"]), ("GL11", ["GL10"]), ("GL11Ext", ["GL"]),
             ("GL11ExtensionPack", ["GL"])]


def javap_iface(name):
    out = subprocess.run(["javap", "-cp", JAR, "-public", "-s", "-constants", name],
                         capture_output=True, text=True, check=True).stdout
    lines = out.splitlines()
    consts, methods = [], []
    i = 0
    while i < len(lines):
        ln = lines[i].strip()
        desc = lines[i + 1].strip()[len("descriptor: "):] if i + 1 < len(lines) and "descriptor:" in lines[i + 1] else None
        if ln.startswith("public static final"):
            consts.append(ln)
        elif "(" in ln and desc:
            methods.append((re.search(r"(\w+)\(", ln).group(1), desc))
        i += 2 if desc else 1
    return consts, methods


def gen_gl_interfaces(java_natives):
    """javax.microedition.khronos.opengles interfaces and com.google.android.gles_jni.GLImpl."""
    pkg_dir = os.path.join(ROOT, "java/framework/javax/microedition/khronos/opengles")
    os.makedirs(pkg_dir, exist_ok=True)
    statics = {}
    for cls in ("GLES10", "GLES10Ext", "GLES11", "GLES11Ext", "GLES20"):
        for name, desc, line in java_natives[cls]:
            statics.setdefault((name, desc), cls)
    impl_methods = {}
    for iface, supers in GL_IFACES:
        consts, methods = javap_iface("javax.microedition.khronos.opengles." + iface)
        jl = ["// Generated by tools/gen_gles.py from android.jar. Do not edit.",
              "package javax.microedition.khronos.opengles;", "",
              "public interface %s%s {" % (iface, " extends " + ", ".join(supers) if supers else "")]
        for c in consts:
            jl.append("    " + c.replace("public static final ", "", 1))
        if consts:
            jl.append("")
        for name, desc in methods:
            jparams, jret = split_desc(desc)
            sig = ", ".join("%s p%d" % (java_type(d), i) for i, d in enumerate(jparams))
            jl.append("    %s %s(%s);" % (java_type(jret), name, sig))
            impl_methods[(name, desc)] = (jparams, jret)
        jl.append("}")
        open(os.path.join(pkg_dir, iface + ".java"), "w").write("\n".join(jl) + "\n")
    jl = ["// Generated by tools/gen_gles.py from android.jar. Do not edit.",
          "package com.google.android.gles_jni;", "",
          "import javax.microedition.khronos.opengles.GL10;",
          "import javax.microedition.khronos.opengles.GL10Ext;",
          "import javax.microedition.khronos.opengles.GL11;",
          "import javax.microedition.khronos.opengles.GL11Ext;",
          "import javax.microedition.khronos.opengles.GL11ExtensionPack;", "",
          "/** The GL object EGLContext.getGL() returns: forwards to the GLES10/GLES11 bindings. */",
          "public class GLImpl implements GL10, GL10Ext, GL11, GL11Ext, GL11ExtensionPack {",
          "    public GLImpl() {", "    }", ""]
    missing = 0
    for (name, desc), (jparams, jret) in sorted(impl_methods.items()):
        sig = ", ".join("%s p%d" % (java_type(d), i) for i, d in enumerate(jparams))
        call_args = ", ".join("p%d" % i for i in range(len(jparams)))
        jl.append("    public %s %s(%s) {" % (java_type(jret), name, sig))
        cls, target = statics.get((name, desc)), name
        if not cls and (name + "OES", desc) in statics:
            # GL11ExtensionPack names the OES texgen functions without the suffix
            cls, target = statics[(name + "OES", desc)], name + "OES"
        if cls:
            jl.append("        %sandroid.opengl.%s.%s(%s);" % ("" if jret == "V" else "return ", cls, target, call_args))
        else:
            missing += 1
            jl.append("        android.opengl.EGLNative.unsupported(\"GL.%s\");" % name)
            if jret != "V":
                jl.append("        return %s;" % ("null" if jret.startswith("L") or jret.startswith("[")
                                                 else "false" if jret == "Z" else "0"))
        jl.append("    }")
        jl.append("")
    jl.append("}")
    d = os.path.join(ROOT, "java/framework/com/google/android/gles_jni")
    os.makedirs(d, exist_ok=True)
    open(os.path.join(d, "GLImpl.java"), "w").write("\n".join(jl) + "\n")
    print("GLImpl: %d methods, %d without a GLES binding" % (len(impl_methods), missing))


def main():
    protos = parse_headers()
    specials = special_regs()
    used = {}
    cfuncs, regs, unmatched, fallbacks = [], [], [], []
    java_out = {}
    java_natives = {}
    for cls, sup in CLASSES:
        consts, natives, others = javap(cls)
        java_natives[cls] = natives
        jl = ["// Generated by tools/gen_gles.py from android.jar and the Khronos headers. Do not edit.",
              "package android.opengl;", "", ""]
        if cls == "GLES31Ext":
            jl[2] = ""
        head = "public class %s%s {" % (cls, " extends " + sup if sup else "")
        jl.append(head)
        for c in consts:
            jl.append("    " + c.replace("public static final", "public static final", 1))
        jl.append("")
        if cls == "GLES31Ext":
            jl.append("    public interface DebugProcKHR {")
            jl.append("        void onMessage(int source, int type, int id, int severity, String message);")
            jl.append("    }")
            jl.append("")
        if cls == "GLES32":
            jl.append("    public interface DebugProc {")
            jl.append("        void onMessage(int source, int type, int id, int severity, String message);")
            jl.append("    }")
            jl.append("")
        ctor_vis = "public"
        jl.append("    %s %s() {" % (ctor_vis, cls))
        jl.append("    }")
        jl.append("")
        overloads = {}
        for name, desc, line in natives:
            jparams, jret = split_desc(desc)
            k = overloads.get(name, 0)
            overloads[name] = k + 1
            fn = "n_%s_%s%s" % (cls, name, "_%d" % k if k else "")
            proto = protos.get(name)
            plan = match(jparams, proto[1]) if proto else None
            rt = proto[0] if proto else None
            ret_ok = plan is not None and (
                (jret == "V" and rt == "void")
                or (len(jret) == 1 and rt in SCALARS and jret in SCALARS[rt][1])
                or (jret == "J" and rt == "GLsync")
                or (jret == "Ljava/lang/String;" and rt == "const GLubyte *"))
            # Java parameter names
            names = []
            if plan is not None and ret_ok:
                for kind, cname, jd, ct in plan:
                    names.append(cname)
                    if kind == "array":
                        names.append(cname + "Offset")
            else:
                names = ["p%d" % i for i in range(len(jparams))]
            seen = set()
            for i, n in enumerate(names):
                if n in seen or n in ("native", "class", "int", "char", "byte", "float", "long", "short", "boolean"):
                    names[i] = n + str(i)
                seen.add(names[i])
            jsig = ", ".join("%s %s" % (java_type(d), names[i]) for i, d in enumerate(jparams))
            jl.append("    public static native %s %s(%s);" % (java_type(jret), name, jsig))
            if (cls, name, desc) in specials:
                continue
            if not (plan is not None and ret_ok):
                unmatched.append("%s.%s%s" % (cls, name, desc))
                fallbacks.append((cls, name, desc, jret, fn))
                continue
            used[name] = proto
            body = []
            cargs = []
            slot = 0
            frees = []
            for kind, cname, jd, ct in plan:
                v = "a_" + cname
                if kind == "scalar":
                    if ct == "void *":
                        body.append("    %s %s = (void *)(intptr_t)%s;" % (ct, v, arg_reader(jd, slot)))
                    else:
                        body.append("    %s %s = (%s)%s;" % (ct, v, ct, arg_reader(jd, slot)))
                    slot += slots(jd)
                elif kind == "array":
                    body.append("    %s %s = gles_array(t, A_ARR(%d), A_INT(%d), %d, \"%s\");"
                                % (ct, v, slot, slot + 1, ARRAY_ELEM[jd], cname))
                    body.append("    if (t->exception) return;")
                    slot += 2
                elif kind == "buffer":
                    body.append("    %s %s = gles_buffer(A_OBJ(%d));" % (ct, v, slot))
                    slot += 1
                elif kind == "string":
                    body.append("    char *%s = nat_str(A_OBJ(%d));" % (v, slot))
                    frees.append(v)
                    slot += 1
                elif kind == "offset":
                    body.append("    %s %s = (void *)(intptr_t)%s;" % (ct, v, arg_reader(jd, slot)))
                    slot += slots(jd)
                cargs.append(v)
            call = "sa_gl.%s(%s)" % (name, ", ".join(cargs))
            pre = ["    if (!sa_gl.%s) {" % name, "        gles_missing(\"%s\");" % name]
            if jret != "V":
                pre.append("        R_INT(0);" if jret != "Ljava/lang/String;" and jret != "J" else
                           ("        R_OBJ(NULL);" if jret.startswith("L") else "        R_LONG(0);"))
            pre += ["        return;", "    }"]
            lines = ["NATIVE(%s) {" % fn, "    UNUSED_ARGS();"] + pre + body
            block = name in BLOCKING
            if block:
                lines.append("    vm_gil_release(t);")
            if jret == "V":
                lines.append("    %s;" % call)
            elif jret == "Ljava/lang/String;":
                lines.append("    const char *r = (const char *)%s;" % call)
            elif jret == "J" and rt == "GLsync":
                lines.append("    int64_t r = (int64_t)(intptr_t)%s;" % call)
            else:
                lines.append("    %s r = %s;" % (SCALARS[rt][0], call))
            if block:
                lines.append("    vm_gil_acquire(t);")
            for f in frees:
                lines.append("    free(%s);" % f)
            if jret == "Ljava/lang/String;":
                lines.append("    R_OBJ(r ? vm_new_string_utf8(t, r) : NULL);")
            elif jret == "Z":
                lines.append("    R_BOOL(r);")
            elif jret == "F":
                lines.append("    R_FLOAT(r);")
            elif jret == "J":
                lines.append("    R_LONG(r);")
            elif jret != "V":
                lines.append("    R_INT(r);")
            lines.append("}")
            cfuncs.append("\n".join(lines))
            regs.append('    {"Landroid/opengl/%s;", "%s", "%s", %s},' % (cls, name, desc, fn))
        jl.append("}")
        java_out[cls] = "\n".join(jl) + "\n"

    # entry points the special file calls directly
    for name in ("glGetString", "glShaderSource", "glGetShaderiv", "glGetShaderInfoLog", "glGetProgramiv",
                 "glGetProgramInfoLog", "glGetShaderSource", "glGetActiveAttrib", "glGetActiveUniform",
                 "glGetTransformFeedbackVarying", "glTransformFeedbackVaryings", "glGetUniformIndices",
                 "glMapBufferRange", "glGetBufferParameteriv", "glGetBufferPointerv", "glGetActiveUniformBlockName",
                 "glGetActiveUniformBlockiv", "glGetProgramPipelineInfoLog", "glGetProgramPipelineiv",
                 "glGetProgramResourceName", "glCreateShaderProgramv", "glGetObjectLabel", "glGetObjectPtrLabel",
                 "glGetStringi", "glReadPixels", "glGetError", "glPixelStorei", "glGetIntegerv",
                 "glTexImage2D", "glTexSubImage2D", "glBindTexture", "glGenTextures", "glDeleteTextures",
                 "glViewport", "glClearColor", "glClear", "glEnable", "glDisable", "glBlendFunc", "glUseProgram",
                 "glBindFramebuffer", "glFinish", "glFlush", "glDebugMessageCallback", "glDebugMessageCallbackKHR",
                 "glObjectPtrLabel", "glGetPointerv", "glBindBuffer"):
        if name in protos:
            used.setdefault(name, protos[name])

    # gles_funcs.h
    fl = ["/* Generated by tools/gen_gles.py from the Khronos headers. Do not edit. */",
          "/* X(return type, name, (parameter list)) for every GL ES entry point the bindings use. */",
          "#define SA_GL_FUNCS(X) \\"]
    for name in sorted(used):
        ret, plist = used[name]
        if "*" in ret:
            rt = "const void *" if ret.startswith("const") else "void *"
        else:
            rt = "void" if ret == "void" else SCALARS[ret][0]
        cps = []
        ok = True
        for p in plist:
            base, ptr, pname, decl = c_param(p)
            if base in ("GLDEBUGPROC", "GLDEBUGPROCKHR"):
                cps.append("void *" + pname)
                continue
            try:
                ct = c_type(base, ptr, decl)
            except KeyError:
                ok = False
                break
            if decl.replace(" ", "").count("*") > 1:
                ct = "const char *const *" if "GLchar" in decl else "void **"
            cps.append("%s%s%s" % (ct, "" if ct.endswith("*") else " ", pname))
        if not ok:
            continue
        fl.append("    X(%s, %s, (%s)) \\" % (rt, name, ", ".join(cps) if cps else "void"))
    fl.append("    /* end */")
    open(os.path.join(ROOT, "src/android/gles_funcs.h"), "w").write("\n".join(fl) + "\n")

    # fallbacks for unmatched methods without a hand-written implementation
    for cls, name, desc, jret, fn in fallbacks:
        lines = ["NATIVE(%s) {" % fn, "    UNUSED_ARGS();", "    gles_unsupported(\"%s.%s\");" % (cls, name)]
        if jret.startswith("L") or jret.startswith("["):
            lines.append("    R_OBJ(NULL);")
        elif jret == "J":
            lines.append("    R_LONG(0);")
        elif jret != "V":
            lines.append("    R_INT(0);")
        lines.append("}")
        cfuncs.append("\n".join(lines))
        regs.append('    {"Landroid/opengl/%s;", "%s", "%s", %s},' % (cls, name, desc, fn))

    out = ["/* Generated by tools/gen_gles.py from android.jar and the Khronos headers. Do not edit. */",
           "#include \"android_gl.h\"", "", "#define LOG_TAG \"gles\"", ""]
    out += [f + "\n" for f in cfuncs]
    out.append("static const NativeMethodReg g_regs[] = {")
    out += regs
    out.append("};")
    out.append("")
    out.append("void android_gles_gen_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }")
    open(os.path.join(ROOT, "src/android/android_gles_gen.c"), "w").write("\n".join(out) + "\n")

    gen_gl_interfaces(java_natives)

    for cls, text in java_out.items():
        open(os.path.join(ROOT, "java/framework/android/opengl/%s.java" % cls), "w").write(text)

    print("generated %d natives, %d entry points; %d need hand-written code:" % (len(regs), len(used),
                                                                                   len(unmatched)))
    for u in unmatched:
        print("  " + u)


if __name__ == "__main__":
    main()
