/*
 * Copyright (C) 2017 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "com_twilio_video_IceOptions.h"

#include "webrtc/sdk/android/src/jni/jni_helpers.h"

#include "video/video.h"
#include "jni_utils.h"
#include "webrtc/modules/utility/include/helpers_android.h"

namespace twilio_video_jni {

twilio::media::IceOptions IceOptions::getIceOptions(JNIEnv *env, jobject j_ice_options) {
    jclass j_ice_options_class = GetObjectClass(env, j_ice_options);
    jmethodID j_get_ice_servers_array_id =
            webrtc::GetMethodID(env, j_ice_options_class,
                                "getIceServersArray", "()[Lcom/twilio/video/IceServer;");
    jmethodID j_get_ice_transport_policy_id =
            webrtc::GetMethodID(env, j_ice_options_class,
                                "getIceTransportPolicy", "()Lcom/twilio/video/IceTransportPolicy;");
    jobjectArray j_ice_servers =
            (jobjectArray) env->CallObjectMethod(j_ice_options, j_get_ice_servers_array_id);
    jobject j_ice_trans_policy =
            env->CallObjectMethod(j_ice_options, j_get_ice_transport_policy_id);

    twilio::media::IceOptions ice_options;
    twilio::media::IceServers ice_servers;
    if (!IsNull(env, j_ice_servers)) {
        int size = env->GetArrayLength(j_ice_servers);
        if (size != 0) {
            // Adding IceServers
            for (int i=0; i<size; i++) {
                twilio::media::IceServer ice_server;

                jobject j_ice_server = (jobject)env->GetObjectArrayElement(j_ice_servers, i);
                jclass j_ice_server_class = env->GetObjectClass(j_ice_server);
                jfieldID j_server_url_field = env->GetFieldID(j_ice_server_class,
                                                              "serverUrl", "Ljava/lang/String;");
                jfieldID j_username_field = env->GetFieldID(j_ice_server_class,
                                                            "username", "Ljava/lang/String;");
                jfieldID j_password_field = env->GetFieldID(j_ice_server_class,
                                                            "password", "Ljava/lang/String;");
                jstring j_server_url = (jstring)env->GetObjectField(j_ice_server,
                                                                    j_server_url_field);
                jstring j_username = (jstring)env->GetObjectField(j_ice_server, j_username_field);
                jstring j_password = (jstring)env->GetObjectField(j_ice_server, j_password_field);
                std::string server_url = JavaToUTF8StdString(env, j_server_url);
                std::vector<std::string> urls;
                urls.push_back(server_url);
                ice_server.urls = urls;

                if (!IsNull(env, j_username)) {
                    std::string username  = JavaToUTF8StdString(env, j_username);
                    if (username.length() > 0) {
                        ice_server.username = username;
                    }
                }

                if (!IsNull(env, j_password)) {
                    std::string password  = JavaToUTF8StdString(env, j_password);
                    if (password.length() > 0) {
                        ice_server.password = password;
                    }
                }

                ice_servers.push_back(ice_server);
            }
            ice_options.ice_servers = ice_servers;
        }
    }

    if (!IsNull(env, j_ice_trans_policy)) {
        jclass ice_policy_class = env->GetObjectClass(j_ice_trans_policy);
        jmethodID name_id = env->GetMethodID(ice_policy_class, "name", "()Ljava/lang/String;");
        jstring j_ice_policy = (jstring)env->CallObjectMethod(j_ice_trans_policy, name_id);
        std::string ice_policy = JavaToUTF8StdString(env, j_ice_policy);

        if (ice_policy.compare("ICE_TRANSPORT_POLICY_RELAY") == 0) {
            ice_options.ice_transport_policy =
                    twilio::media::IceTransportPolicy::kIceTransportPolicyRelay;
        } else {
            ice_options.ice_transport_policy =
                    twilio::media::IceTransportPolicy::kIceTransportPolicyAll;
        }
    }
    return ice_options;
}
}

