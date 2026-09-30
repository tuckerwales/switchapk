/*
 * Minimal JNI type definitions (ABI compatible with the NDK's jni.h).
 */
#ifndef SWITCHAPK_JNI_TYPES_H
#define SWITCHAPK_JNI_TYPES_H

#include <stdint.h>
#include <stdarg.h>

typedef uint8_t jboolean;
typedef int8_t jbyte;
typedef uint16_t jchar;
typedef int16_t jshort;
typedef int32_t jint;
typedef int64_t jlong;
typedef float jfloat;
typedef double jdouble;
typedef jint jsize;

typedef void *jobject;
typedef jobject jclass;
typedef jobject jstring;
typedef jobject jarray;
typedef jobject jobjectArray;
typedef jobject jbooleanArray;
typedef jobject jbyteArray;
typedef jobject jcharArray;
typedef jobject jshortArray;
typedef jobject jintArray;
typedef jobject jlongArray;
typedef jobject jfloatArray;
typedef jobject jdoubleArray;
typedef jobject jthrowable;
typedef jobject jweak;

typedef void *jfieldID;
typedef void *jmethodID;

typedef union jvalue {
    jboolean z;
    jbyte b;
    jchar c;
    jshort s;
    jint i;
    jlong j;
    jfloat f;
    jdouble d;
    jobject l;
} jvalue;

typedef enum { JNIInvalidRefType = 0, JNILocalRefType = 1, JNIGlobalRefType = 2, JNIWeakGlobalRefType = 3 } jobjectRefType;

typedef struct {
    const char *name;
    const char *signature;
    void *fnPtr;
} JNINativeMethod;

#define JNI_FALSE 0
#define JNI_TRUE 1
#define JNI_OK 0
#define JNI_ERR (-1)
#define JNI_EDETACHED (-2)
#define JNI_EVERSION (-3)
#define JNI_ENOMEM (-4)
#define JNI_EEXIST (-5)
#define JNI_EINVAL (-6)
#define JNI_COMMIT 1
#define JNI_ABORT 2
#define JNI_VERSION_1_1 0x00010001
#define JNI_VERSION_1_2 0x00010002
#define JNI_VERSION_1_4 0x00010004
#define JNI_VERSION_1_6 0x00010006

/* JNIEnv / JavaVM are pointers to structs whose first member is the function table. */
typedef struct SaJNIEnv {
    const void *const *functions;
    struct VMThread *thread;
} SaJNIEnv;

typedef struct SaJavaVM {
    const void *const *functions;
} SaJavaVM;

typedef struct {
    jint version;
    const char *name;
    jobject group;
} JavaVMAttachArgs;

#endif
