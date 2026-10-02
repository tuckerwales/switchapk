/*
 * JNI test library for tests/apps/ndk. Built for an Android target without
 * the NDK: bionic functions are declared by hand so the imports are exactly
 * what an NDK build would import.
 */
#include <jni.h>
#include <stddef.h>
#include <stdint.h>

/* bionic libc / liblog / libandroid / libdl imports */
void *malloc(size_t size);
void free(void *p);
size_t strlen(const char *s);
int strcmp(const char *a, const char *b);
void qsort(void *base, size_t n, size_t size, int (*cmp)(const void *, const void *));
double strtod(const char *s, char **end);
float sinf(float x);
int pthread_create(long *thread, const void *attr, void *(*fn)(void *), void *arg);
int pthread_join(long thread, void **ret);
int pthread_mutex_lock(void *m);
int pthread_mutex_unlock(void *m);
int __android_log_print(int prio, const char *tag, const char *fmt, ...);
void *AAssetManager_fromJava(JNIEnv *env, jobject assetManager);
void *AAssetManager_open(void *mgr, const char *name, int mode);
int AAsset_read(void *asset, void *buf, size_t count);
long AAsset_getLength(void *asset);
void AAsset_close(void *asset);
void *dlopen(const char *name, int flags);
void *dlsym(void *handle, const char *name);

/* from libndkdep.so */
int dep_value(void);
int dep_ctor_state(void);

/* provided by nobody: the loader binds it to a stub that logs and returns 0 */
int switchapk_missing_function(int x);

#define TAG "ndktest"

static JavaVM *g_vm;
static int g_onload_ran;

/* bionic's PTHREAD_MUTEX_INITIALIZER: 40 zero bytes */
static struct {
    int32_t state[10];
} g_mutex;
static int g_counter;

JNIEXPORT jstring JNICALL Java_com_example_ndk_Native_hello(JNIEnv *env, jclass cls, jstring name) {
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    char buf[256];
    snprintf(buf, sizeof buf, "Hello, %s from C", n);
    (*env)->ReleaseStringUTFChars(env, name, n);
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jint JNICALL Java_com_example_ndk_Native_sum(JNIEnv *env, jclass cls, jintArray values) {
    jsize n = (*env)->GetArrayLength(env, values);
    jint *v = (*env)->GetIntArrayElements(env, values, NULL);
    jint s = 0;
    for (jsize i = 0; i < n; i++) s += v[i];
    (*env)->ReleaseIntArrayElements(env, values, v, JNI_ABORT);
    return s;
}

JNIEXPORT jbyteArray JNICALL Java_com_example_ndk_Native_bytes(JNIEnv *env, jclass cls, jint n) {
    jbyteArray a = (*env)->NewByteArray(env, n);
    jbyte *tmp = malloc((size_t)n);
    for (jint i = 0; i < n; i++) tmp[i] = (jbyte)(i * 3);
    (*env)->SetByteArrayRegion(env, a, 0, n, tmp);
    free(tmp);
    return a;
}

JNIEXPORT jint JNICALL Java_com_example_ndk_Native_callback(JNIEnv *env, jclass cls, jobject cb, jint x) {
    jclass c = (*env)->GetObjectClass(env, cb);
    jmethodID m = (*env)->GetMethodID(env, c, "onValue", "(I)I");
    if (!m) return -1;
    return 2 * (*env)->CallIntMethod(env, cb, m, x);
}

JNIEXPORT void JNICALL Java_com_example_ndk_Native_fail(JNIEnv *env, jclass cls, jstring msg) {
    const char *m = (*env)->GetStringUTFChars(env, msg, NULL);
    jclass ise = (*env)->FindClass(env, "java/lang/IllegalStateException");
    (*env)->ThrowNew(env, ise, m);
    (*env)->ReleaseStringUTFChars(env, msg, m);
}

JNIEXPORT jstring JNICALL Java_com_example_ndk_Native_asset(JNIEnv *env, jclass cls, jobject am, jstring name) {
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    void *mgr = AAssetManager_fromJava(env, am);
    void *a = AAssetManager_open(mgr, n, 0 /* AASSET_MODE_UNKNOWN */);
    (*env)->ReleaseStringUTFChars(env, name, n);
    if (!a) return NULL;
    long len = AAsset_getLength(a);
    char *buf = malloc((size_t)len + 1);
    int got = AAsset_read(a, buf, (size_t)len);
    buf[got > 0 ? got : 0] = 0;
    AAsset_close(a);
    jstring s = (*env)->NewStringUTF(env, buf);
    free(buf);
    return s;
}

static void *count_thread(void *arg) {
    for (int i = 0; i < 1000; i++) {
        pthread_mutex_lock(&g_mutex);
        g_counter++;
        pthread_mutex_unlock(&g_mutex);
    }
    return arg;
}

JNIEXPORT jint JNICALL Java_com_example_ndk_Native_threads(JNIEnv *env, jclass cls, jint n) {
    long t[16];
    g_counter = 0;
    if (n > 16) n = 16;
    for (int i = 0; i < n; i++) pthread_create(&t[i], NULL, count_thread, NULL);
    for (int i = 0; i < n; i++) pthread_join(t[i], NULL);
    return g_counter;
}

static jint registered(JNIEnv *env, jclass cls, jint x) { return x * 7; }

JNIEXPORT jint JNICALL Java_com_example_ndk_Native_depValue(JNIEnv *env, jclass cls) {
    return dep_value() + 100 * dep_ctor_state() + 1000 * g_onload_ran;
}

JNIEXPORT jint JNICALL Java_com_example_ndk_Native_missing(JNIEnv *env, jclass cls) {
    return switchapk_missing_function(5);
}

JNIEXPORT jstring JNICALL Java_com_example_ndk_Native_format(JNIEnv *env, jclass cls, jdouble d) {
    char buf[128];
    snprintf(buf, sizeof buf, "%.3f|%s|%d|%x", d, "str", -12, 255);
    return (*env)->NewStringUTF(env, buf);
}

static int cmp_int(const void *a, const void *b) { return *(const int *)a - *(const int *)b; }

/* qsort with a callback into this library, strtod, and libm */
JNIEXPORT jint JNICALL Java_com_example_ndk_Native_libc(JNIEnv *env, jclass cls) {
    int v[] = {5, 3, 9, 1, 7};
    qsort(v, 5, sizeof v[0], cmp_int);
    double d = strtod("2.5", NULL);
    float s = sinf(0.0f);
    int ok = v[0] == 1 && v[4] == 9 && d == 2.5 && s == 0.0f && strlen("four") == 4 && strcmp("a", "b") < 0;
    return ok ? 1 : 0;
}

typedef struct {
    jobject cb;
    jint result;
} ThreadArg;

static void *attach_thread(void *p) {
    ThreadArg *a = p;
    JNIEnv *env;
    if ((*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL) != JNI_OK) return NULL;
    jclass c = (*env)->GetObjectClass(env, a->cb);
    jmethodID m = (*env)->GetMethodID(env, c, "onValue", "(I)I");
    a->result = (*env)->CallIntMethod(env, a->cb, m, 21);
    (*g_vm)->DetachCurrentThread(g_vm);
    return NULL;
}

JNIEXPORT jint JNICALL Java_com_example_ndk_Native_fromThread(JNIEnv *env, jclass cls, jobject cb) {
    ThreadArg a = {(*env)->NewGlobalRef(env, cb), -1};
    long t;
    pthread_create(&t, NULL, attach_thread, &a);
    pthread_join(t, NULL);
    (*env)->DeleteGlobalRef(env, a.cb);
    return a.result;
}

JNIEXPORT jint JNICALL Java_com_example_ndk_Native_dlsymDep(JNIEnv *env, jclass cls) {
    void *h = dlopen("libndkdep.so", 2 /* RTLD_NOW */);
    if (!h) return -1;
    int (*f)(void) = (int (*)(void))dlsym(h, "dep_value");
    return f ? f() : -2;
}

JNIEXPORT jint JNICALL Java_com_example_ndk_Native_fieldSum(JNIEnv *env, jclass cls, jobject point) {
    jclass c = (*env)->GetObjectClass(env, point);
    jfieldID x = (*env)->GetFieldID(env, c, "x", "I");
    jfieldID y = (*env)->GetFieldID(env, c, "y", "I");
    (*env)->SetIntField(env, point, x, 100);
    return (*env)->GetIntField(env, point, x) + (*env)->GetIntField(env, point, y);
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass c = (*env)->FindClass(env, "com/example/ndk/Native");
    JNINativeMethod methods[] = {{"registered", "(I)I", (void *)registered}};
    if ((*env)->RegisterNatives(env, c, methods, 1) != JNI_OK) return JNI_ERR;
    g_onload_ran = 1;
    __android_log_print(4 /* ANDROID_LOG_INFO */, TAG, "JNI_OnLoad ran, vm %p", (void *)vm);
    return JNI_VERSION_1_6;
}
