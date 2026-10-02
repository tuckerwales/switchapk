/*
 * Hand-written GLES natives: the methods tools/gen_gles.py cannot pair with
 * a C prototype (strings returned through a sized buffer, String[] inputs,
 * mapped buffers). The generator reads the registration table below and
 * leaves these methods out of android_gles_gen.c; it emits a logging
 * fallback for any irregular method missing here (the debug callbacks and
 * message logs for now).
 */
#include "android_gl.h"

#define LOG_TAG "gles"

#define GL_INFO_LOG_LENGTH 0x8B84
#define GL_SHADER_SOURCE_LENGTH 0x8B88
#define GL_ACTIVE_UNIFORM_MAX_LENGTH 0x8B87
#define GL_ACTIVE_ATTRIBUTE_MAX_LENGTH 0x8B8A
#define GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH 0x8C76
#define GL_UNIFORM_BLOCK_NAME_LENGTH 0x8A41
#define GL_BUFFER_MAP_LENGTH 0x9120
#define GL_BUFFER_MAP_POINTER 0x88BD

#define NEED(fn, retstmt)          \
    do {                           \
        if (!sa_gl.fn) {           \
            gles_missing(#fn);     \
            retstmt;               \
            return;                \
        }                          \
    } while (0)

static Object *new_string_n(VMThread *t, const char *s, int32_t len) {
    if (len < 0) len = 0;
    char *tmp = sa_malloc((size_t)len + 1);
    memcpy(tmp, s, (size_t)len);
    tmp[len] = 0;
    Object *r = vm_new_string_utf8(t, tmp);
    free(tmp);
    return r;
}

/* Converts a String[] to a malloc'd array of malloc'd UTF-8 strings. */
static char **string_array(VMThread *t, ArrayObject *a, int32_t *count) {
    if (!a) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "varyings == null");
        return NULL;
    }
    char **v = sa_calloc((size_t)a->length + 1, sizeof(char *));
    for (int32_t i = 0; i < a->length; i++) {
        Object *s = ARRAY_DATA(a, Object *)[i];
        v[i] = s ? vm_string_to_utf8(s) : sa_strdup("");
    }
    *count = a->length;
    return v;
}

static void free_strings(char **v, int32_t n) {
    if (!v) return;
    for (int32_t i = 0; i < n; i++) free(v[i]);
    free(v);
}

/* A direct ByteBuffer over GL-owned memory (like JNI NewDirectByteBuffer). */
static Object *direct_buffer(VMThread *t, void *p, int32_t len) {
    if (!p) return NULL;
    ArrayObject *a = vm_alloc_prim_array(t, 'B', 0);
    if (!a) return NULL;
    a->data = p;
    a->length = len;
    JValue bb = vm_call_static(t, "Ljava/nio/ByteBuffer;", "wrap", "([B)Ljava/nio/ByteBuffer;", (Object *)a);
    if (t->exception || !bb.l) return NULL;
    vm_set_int_by_name(bb.l, "direct", 1);
    return bb.l;
}

/* ---- shaders and programs ---------------------------------------------------------------------------- */

NATIVE(n_glShaderSource) {
    UNUSED_ARGS();
    NEED(glShaderSource, (void)0);
    char *src = nat_str(A_OBJ(1));
    if (!src) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "string == null");
        return;
    }
    const char *strs[1] = {src};
    int32_t len = (int32_t)strlen(src);
    sa_gl.glShaderSource((uint32_t)A_INT(0), 1, strs, &len);
    free(src);
}

typedef void (*GetivFn)(uint32_t, uint32_t, void *);
typedef void (*GetLogFn)(uint32_t, int32_t, void *, void *);

static Object *get_log(VMThread *t, uint32_t obj, GetivFn getiv, uint32_t pname, GetLogFn getlog) {
    int32_t len = 0;
    getiv(obj, pname, &len);
    if (len <= 0) return vm_new_string_utf8(t, "");
    char *buf = sa_malloc((size_t)len + 1);
    int32_t got = 0;
    getlog(obj, len + 1, &got, buf);
    Object *r = new_string_n(t, buf, got);
    free(buf);
    return r;
}

NATIVE(n_glGetShaderInfoLog) {
    UNUSED_ARGS();
    NEED(glGetShaderInfoLog, R_OBJ(NULL));
    R_OBJ(get_log(t, (uint32_t)A_INT(0), (GetivFn)sa_gl.glGetShaderiv, GL_INFO_LOG_LENGTH,
                  (GetLogFn)sa_gl.glGetShaderInfoLog));
}

NATIVE(n_glGetProgramInfoLog) {
    UNUSED_ARGS();
    NEED(glGetProgramInfoLog, R_OBJ(NULL));
    R_OBJ(get_log(t, (uint32_t)A_INT(0), (GetivFn)sa_gl.glGetProgramiv, GL_INFO_LOG_LENGTH,
                  (GetLogFn)sa_gl.glGetProgramInfoLog));
}

NATIVE(n_glGetShaderSource) {
    UNUSED_ARGS();
    NEED(glGetShaderSource, R_OBJ(NULL));
    R_OBJ(get_log(t, (uint32_t)A_INT(0), (GetivFn)sa_gl.glGetShaderiv, GL_SHADER_SOURCE_LENGTH,
                  (GetLogFn)sa_gl.glGetShaderSource));
}

NATIVE(n_glGetProgramPipelineInfoLog) {
    UNUSED_ARGS();
    NEED(glGetProgramPipelineInfoLog, R_OBJ(NULL));
    R_OBJ(get_log(t, (uint32_t)A_INT(0), (GetivFn)sa_gl.glGetProgramPipelineiv, GL_INFO_LOG_LENGTH,
                  (GetLogFn)sa_gl.glGetProgramPipelineInfoLog));
}

/* ---- active attributes, uniforms and varyings returning their name ------------------------------------ */

typedef void (*GetActiveFn)(uint32_t, uint32_t, int32_t, void *, void *, void *, void *);

static Object *get_active(VMThread *t, GetActiveFn fn, uint32_t maxlen_pname, uint32_t program, uint32_t index,
                          int32_t *size, int32_t *type) {
    int32_t maxlen = 0;
    sa_gl.glGetProgramiv(program, maxlen_pname, &maxlen);
    if (maxlen <= 0) maxlen = 256;
    char *buf = sa_malloc((size_t)maxlen + 1);
    int32_t len = 0;
    fn(program, index, maxlen, &len, size, type, buf);
    Object *r = new_string_n(t, buf, len);
    free(buf);
    return r;
}

/* (int program, int index, int[] size, int sizeOffset, int[] type, int typeOffset) */
static void active_arrays(VMThread *t, uint64_t *args, JValue *ret, GetActiveFn fn, uint32_t pname) {
    int32_t *size = gles_array(t, A_ARR(2), A_INT(3), 4, "size");
    if (t->exception) return;
    int32_t *type = gles_array(t, A_ARR(4), A_INT(5), 4, "type");
    if (t->exception) return;
    R_OBJ(get_active(t, fn, pname, (uint32_t)A_INT(0), (uint32_t)A_INT(1), size, type));
}

/* (int program, int index, IntBuffer size, IntBuffer type) */
static void active_buffers(VMThread *t, uint64_t *args, JValue *ret, GetActiveFn fn, uint32_t pname) {
    R_OBJ(get_active(t, fn, pname, (uint32_t)A_INT(0), (uint32_t)A_INT(1), gles_buffer(A_OBJ(2)),
                     gles_buffer(A_OBJ(3))));
}

NATIVE(n_glGetActiveAttrib_a) {
    NEED(glGetActiveAttrib, R_OBJ(NULL));
    active_arrays(t, args, ret, (GetActiveFn)sa_gl.glGetActiveAttrib, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH);
}

NATIVE(n_glGetActiveAttrib_b) {
    NEED(glGetActiveAttrib, R_OBJ(NULL));
    active_buffers(t, args, ret, (GetActiveFn)sa_gl.glGetActiveAttrib, GL_ACTIVE_ATTRIBUTE_MAX_LENGTH);
}

NATIVE(n_glGetActiveUniform_a) {
    NEED(glGetActiveUniform, R_OBJ(NULL));
    active_arrays(t, args, ret, (GetActiveFn)sa_gl.glGetActiveUniform, GL_ACTIVE_UNIFORM_MAX_LENGTH);
}

NATIVE(n_glGetActiveUniform_b) {
    NEED(glGetActiveUniform, R_OBJ(NULL));
    active_buffers(t, args, ret, (GetActiveFn)sa_gl.glGetActiveUniform, GL_ACTIVE_UNIFORM_MAX_LENGTH);
}

NATIVE(n_glGetTransformFeedbackVarying_a) {
    NEED(glGetTransformFeedbackVarying, R_OBJ(NULL));
    active_arrays(t, args, ret, (GetActiveFn)sa_gl.glGetTransformFeedbackVarying,
                  GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH);
}

NATIVE(n_glGetTransformFeedbackVarying_b) {
    NEED(glGetTransformFeedbackVarying, R_OBJ(NULL));
    active_buffers(t, args, ret, (GetActiveFn)sa_gl.glGetTransformFeedbackVarying,
                   GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH);
}

NATIVE(n_glGetActiveUniformBlockName) {
    UNUSED_ARGS();
    NEED(glGetActiveUniformBlockName, R_OBJ(NULL));
    uint32_t program = (uint32_t)A_INT(0), index = (uint32_t)A_INT(1);
    int32_t len = 0;
    if (sa_gl.glGetActiveUniformBlockiv) sa_gl.glGetActiveUniformBlockiv(program, index, GL_UNIFORM_BLOCK_NAME_LENGTH, &len);
    if (len <= 0) len = 256;
    char *buf = sa_malloc((size_t)len + 1);
    int32_t got = 0;
    sa_gl.glGetActiveUniformBlockName(program, index, len, &got, buf);
    R_OBJ(new_string_n(t, buf, got));
    free(buf);
}

/* (int program, int uniformBlockIndex, Buffer length, Buffer uniformBlockName) */
NATIVE(n_glGetActiveUniformBlockName_b) {
    UNUSED_ARGS();
    NEED(glGetActiveUniformBlockName, (void)0);
    Object *name = A_OBJ(3);
    sa_gl.glGetActiveUniformBlockName((uint32_t)A_INT(0), (uint32_t)A_INT(1), gles_buffer_remaining_bytes(name),
                                      gles_buffer(A_OBJ(2)), gles_buffer(name));
}

NATIVE(n_glGetProgramResourceName) {
    UNUSED_ARGS();
    NEED(glGetProgramResourceName, R_OBJ(NULL));
    char buf[1024];
    int32_t len = 0;
    sa_gl.glGetProgramResourceName((uint32_t)A_INT(0), (uint32_t)A_INT(1), (uint32_t)A_INT(2), sizeof buf, &len,
                                   buf);
    R_OBJ(new_string_n(t, buf, len));
}

/* ---- String[] inputs --------------------------------------------------------------------------------- */

NATIVE(n_glTransformFeedbackVaryings) {
    UNUSED_ARGS();
    NEED(glTransformFeedbackVaryings, (void)0);
    int32_t n = 0;
    char **v = string_array(t, A_ARR(1), &n);
    if (!v) return;
    sa_gl.glTransformFeedbackVaryings((uint32_t)A_INT(0), n, (const char *const *)v, (uint32_t)A_INT(2));
    free_strings(v, n);
}

static void uniform_indices(VMThread *t, uint32_t program, ArrayObject *names, void *out) {
    int32_t n = 0;
    char **v = string_array(t, names, &n);
    if (!v) return;
    sa_gl.glGetUniformIndices(program, n, (const char *const *)v, out);
    free_strings(v, n);
}

NATIVE(n_glGetUniformIndices_a) {
    UNUSED_ARGS();
    NEED(glGetUniformIndices, (void)0);
    void *out = gles_array(t, A_ARR(2), A_INT(3), 4, "uniformIndices");
    if (t->exception) return;
    uniform_indices(t, (uint32_t)A_INT(0), A_ARR(1), out);
}

NATIVE(n_glGetUniformIndices_b) {
    UNUSED_ARGS();
    NEED(glGetUniformIndices, (void)0);
    uniform_indices(t, (uint32_t)A_INT(0), A_ARR(1), gles_buffer(A_OBJ(2)));
}

NATIVE(n_glCreateShaderProgramv) {
    UNUSED_ARGS();
    NEED(glCreateShaderProgramv, R_INT(0));
    int32_t n = 0;
    char **v = string_array(t, A_ARR(1), &n);
    if (!v) return;
    R_INT(sa_gl.glCreateShaderProgramv((uint32_t)A_INT(0), n, (const char *const *)v));
    free_strings(v, n);
}

/* ---- mapped buffers ---------------------------------------------------------------------------------- */

NATIVE(n_glMapBufferRange) {
    UNUSED_ARGS();
    NEED(glMapBufferRange, R_OBJ(NULL));
    int32_t length = A_INT(2);
    void *p = sa_gl.glMapBufferRange((uint32_t)A_INT(0), A_INT(1), length, (uint32_t)A_INT(3));
    R_OBJ(direct_buffer(t, p, length));
}

NATIVE(n_glGetBufferPointerv) {
    UNUSED_ARGS();
    NEED(glGetBufferPointerv, R_OBJ(NULL));
    uint32_t target = (uint32_t)A_INT(0);
    void *p = NULL;
    sa_gl.glGetBufferPointerv(target, (uint32_t)A_INT(1), (void **)&p);
    int32_t len = 0;
    if (sa_gl.glGetBufferParameteriv) sa_gl.glGetBufferParameteriv(target, GL_BUFFER_MAP_LENGTH, &len);
    R_OBJ(direct_buffer(t, p, len));
}

/* ---- labels ------------------------------------------------------------------------------------------ */

NATIVE(n_glGetObjectLabel) {
    UNUSED_ARGS();
    NEED(glGetObjectLabel, R_OBJ(NULL));
    char buf[1024];
    int32_t len = 0;
    sa_gl.glGetObjectLabel((uint32_t)A_INT(0), (uint32_t)A_INT(1), sizeof buf, &len, buf);
    R_OBJ(new_string_n(t, buf, len));
}

NATIVE(n_glObjectPtrLabel) {
    UNUSED_ARGS();
    NEED(glObjectPtrLabel, (void)0);
    char *label = nat_str(A_OBJ(2));
    sa_gl.glObjectPtrLabel((void *)(intptr_t)A_LONG(0), label ? -1 : 0, label);
    free(label);
}

NATIVE(n_glGetObjectPtrLabel) {
    UNUSED_ARGS();
    NEED(glGetObjectPtrLabel, R_OBJ(NULL));
    char buf[1024];
    int32_t len = 0;
    sa_gl.glGetObjectPtrLabel((void *)(intptr_t)A_LONG(0), sizeof buf, &len, buf);
    R_OBJ(new_string_n(t, buf, len));
}

NATIVE(n_glGetPointerv) {
    UNUSED_ARGS();
    NEED(glGetPointerv, R_LONG(0));
    void *p = NULL;
    sa_gl.glGetPointerv((uint32_t)A_INT(0), (void **)&p);
    R_LONG((int64_t)(intptr_t)p);
}

static const NativeMethodReg g_regs[] = {
    {"Landroid/opengl/GLES20;", "glShaderSource", "(ILjava/lang/String;)V", n_glShaderSource},
    {"Landroid/opengl/GLES20;", "glGetShaderInfoLog", "(I)Ljava/lang/String;", n_glGetShaderInfoLog},
    {"Landroid/opengl/GLES20;", "glGetProgramInfoLog", "(I)Ljava/lang/String;", n_glGetProgramInfoLog},
    {"Landroid/opengl/GLES20;", "glGetShaderSource", "(I)Ljava/lang/String;", n_glGetShaderSource},
    {"Landroid/opengl/GLES20;", "glGetActiveAttrib", "(II[II[II)Ljava/lang/String;", n_glGetActiveAttrib_a},
    {"Landroid/opengl/GLES20;", "glGetActiveAttrib", "(IILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)Ljava/lang/String;",
     n_glGetActiveAttrib_b},
    {"Landroid/opengl/GLES20;", "glGetActiveUniform", "(II[II[II)Ljava/lang/String;", n_glGetActiveUniform_a},
    {"Landroid/opengl/GLES20;", "glGetActiveUniform", "(IILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)Ljava/lang/String;",
     n_glGetActiveUniform_b},
    {"Landroid/opengl/GLES30;", "glGetBufferPointerv", "(II)Ljava/nio/Buffer;", n_glGetBufferPointerv},
    {"Landroid/opengl/GLES30;", "glMapBufferRange", "(IIII)Ljava/nio/Buffer;", n_glMapBufferRange},
    {"Landroid/opengl/GLES30;", "glTransformFeedbackVaryings", "(I[Ljava/lang/String;I)V",
     n_glTransformFeedbackVaryings},
    {"Landroid/opengl/GLES30;", "glGetTransformFeedbackVarying", "(II[II[II)Ljava/lang/String;",
     n_glGetTransformFeedbackVarying_a},
    {"Landroid/opengl/GLES30;", "glGetTransformFeedbackVarying",
     "(IILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)Ljava/lang/String;", n_glGetTransformFeedbackVarying_b},
    {"Landroid/opengl/GLES30;", "glGetUniformIndices", "(I[Ljava/lang/String;[II)V", n_glGetUniformIndices_a},
    {"Landroid/opengl/GLES30;", "glGetUniformIndices", "(I[Ljava/lang/String;Ljava/nio/IntBuffer;)V",
     n_glGetUniformIndices_b},
    {"Landroid/opengl/GLES30;", "glGetActiveUniformBlockName", "(II)Ljava/lang/String;",
     n_glGetActiveUniformBlockName},
    {"Landroid/opengl/GLES30;", "glGetActiveUniformBlockName", "(IILjava/nio/Buffer;Ljava/nio/Buffer;)V",
     n_glGetActiveUniformBlockName_b},
    {"Landroid/opengl/GLES31;", "glGetProgramResourceName", "(III)Ljava/lang/String;", n_glGetProgramResourceName},
    {"Landroid/opengl/GLES31;", "glCreateShaderProgramv", "(I[Ljava/lang/String;)I", n_glCreateShaderProgramv},
    {"Landroid/opengl/GLES31;", "glGetProgramPipelineInfoLog", "(I)Ljava/lang/String;",
     n_glGetProgramPipelineInfoLog},
    {"Landroid/opengl/GLES32;", "glGetObjectLabel", "(II)Ljava/lang/String;", n_glGetObjectLabel},
    {"Landroid/opengl/GLES32;", "glObjectPtrLabel", "(JLjava/lang/String;)V", n_glObjectPtrLabel},
    {"Landroid/opengl/GLES32;", "glGetObjectPtrLabel", "(J)Ljava/lang/String;", n_glGetObjectPtrLabel},
    {"Landroid/opengl/GLES32;", "glGetPointerv", "(I)J", n_glGetPointerv},
};

void android_gles_special_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
