LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := atmo_vulkan
LOCAL_SRC_FILES := vulkan_color_fill.cpp
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra -Wno-missing-field-initializers
LOCAL_LDLIBS := -landroid -ljnigraphics -llog -lvulkan
include $(BUILD_SHARED_LIBRARY)
