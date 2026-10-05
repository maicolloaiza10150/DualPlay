// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#ifndef CHIAKI_JNI_H
#define CHIAKI_JNI_H

#define JNI_VERSION JNI_VERSION_1_6

#define BASE_PACKAGE "com/hdrdualcontroller/app/lib"
#define JNI_FCN(name) Java_com_hdrdualcontroller_app_lib_ChiakiNative_##name

#define E (*env)

JNIEnv *attach_thread_jni();
jobject jnistr_from_ascii(JNIEnv *env, const char *str);

extern JavaVM *global_vm;

#endif