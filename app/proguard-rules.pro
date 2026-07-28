# The Vulkan bridge uses name-based JNI entry points. Keep the class and native
# method names stable in minified release builds.
-keepclasseswithmembernames,includedescriptorclasses class com.app.nosatmosphereeffect.renderer.vulkan.VulkanNative {
    native <methods>;
}
