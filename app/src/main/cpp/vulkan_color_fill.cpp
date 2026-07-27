#include <jni.h>

#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <limits>
#include <optional>
#include <string>
#include <vector>

namespace {

constexpr char kLogTag[] = "AtmoVulkan";
constexpr char kVertexShader[] =
    "shaders/vulkan/colorfill/colorfill.vert.spv";
constexpr char kFragmentShader[] =
    "shaders/vulkan/colorfill/colorfill.frag.spv";
constexpr uint32_t kVulkanApi14 =
    VK_MAKE_API_VERSION(0, 1, 4, 0);
constexpr std::array<uint32_t, 4> kSupportedCoreApiVersions{
    kVulkanApi14,
    VK_API_VERSION_1_3,
    VK_API_VERSION_1_2,
    VK_API_VERSION_1_1
};

void logError(const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
}

bool hasExtension(
    const std::vector<VkExtensionProperties>& extensions,
    const char* required
) {
    return std::any_of(
        extensions.begin(),
        extensions.end(),
        [required](const VkExtensionProperties& extension) {
            return std::strcmp(extension.extensionName, required) == 0;
        }
    );
}

std::vector<VkExtensionProperties> instanceExtensions() {
    uint32_t count = 0;
    if (vkEnumerateInstanceExtensionProperties(nullptr, &count, nullptr) != VK_SUCCESS) {
        return {};
    }
    std::vector<VkExtensionProperties> result(count);
    if (count > 0 &&
        vkEnumerateInstanceExtensionProperties(nullptr, &count, result.data()) != VK_SUCCESS) {
        return {};
    }
    result.resize(count);
    return result;
}

std::vector<VkExtensionProperties> deviceExtensions(VkPhysicalDevice device) {
    uint32_t count = 0;
    if (vkEnumerateDeviceExtensionProperties(device, nullptr, &count, nullptr) != VK_SUCCESS) {
        return {};
    }
    std::vector<VkExtensionProperties> result(count);
    if (count > 0 &&
        vkEnumerateDeviceExtensionProperties(
            device,
            nullptr,
            &count,
            result.data()
        ) != VK_SUCCESS) {
        return {};
    }
    result.resize(count);
    return result;
}

uint32_t supportedCoreApiVersion(uint32_t advertisedVersion) {
    if (VK_API_VERSION_VARIANT(advertisedVersion) != 0) {
        return 0;
    }
    for (uint32_t supported : kSupportedCoreApiVersions) {
        if (advertisedVersion >= supported) return supported;
    }
    return 0;
}

uint32_t loaderCoreApiVersion() {
    auto enumerateVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
        vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion")
    );
    if (enumerateVersion == nullptr) return 0;
    uint32_t version = VK_API_VERSION_1_0;
    if (enumerateVersion(&version) != VK_SUCCESS) return 0;
    return supportedCoreApiVersion(version);
}

uint32_t probeVulkanRuntime() {
    const uint32_t loaderVersion = loaderCoreApiVersion();
    if (loaderVersion < VK_API_VERSION_1_1) return 0;
    const auto extensions = instanceExtensions();
    if (!hasExtension(extensions, VK_KHR_SURFACE_EXTENSION_NAME) ||
        !hasExtension(extensions, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME)) {
        return 0;
    }

    VkApplicationInfo applicationInfo{
        VK_STRUCTURE_TYPE_APPLICATION_INFO
    };
    applicationInfo.pApplicationName = "Atmo Engine probe";
    applicationInfo.applicationVersion = 1;
    applicationInfo.pEngineName = "Atmo Engine";
    applicationInfo.engineVersion = 1;
    applicationInfo.apiVersion = loaderVersion;

    VkInstanceCreateInfo instanceInfo{
        VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO
    };
    instanceInfo.pApplicationInfo = &applicationInfo;

    VkInstance instance = VK_NULL_HANDLE;
    if (vkCreateInstance(&instanceInfo, nullptr, &instance) != VK_SUCCESS) {
        return 0;
    }

    uint32_t deviceCount = 0;
    uint32_t supportedVersion = 0;
    if (vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr) == VK_SUCCESS &&
        deviceCount > 0) {
        std::vector<VkPhysicalDevice> devices(deviceCount);
        if (vkEnumeratePhysicalDevices(
                instance,
                &deviceCount,
                devices.data()
            ) == VK_SUCCESS) {
            for (VkPhysicalDevice device : devices) {
                VkPhysicalDeviceProperties properties{};
                vkGetPhysicalDeviceProperties(device, &properties);
                const uint32_t deviceVersion =
                    supportedCoreApiVersion(properties.apiVersion);
                if (properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU ||
                    deviceVersion < VK_API_VERSION_1_1) {
                    continue;
                }
                const auto availableDeviceExtensions = deviceExtensions(device);
                if (!hasExtension(
                        availableDeviceExtensions,
                        VK_KHR_SWAPCHAIN_EXTENSION_NAME
                    )) {
                    continue;
                }

                uint32_t queueCount = 0;
                vkGetPhysicalDeviceQueueFamilyProperties(
                    device,
                    &queueCount,
                    nullptr
                );
                std::vector<VkQueueFamilyProperties> queues(queueCount);
                vkGetPhysicalDeviceQueueFamilyProperties(
                    device,
                    &queueCount,
                    queues.data()
                );
                const bool hasGraphicsQueue = std::any_of(
                    queues.begin(),
                    queues.end(),
                    [](const VkQueueFamilyProperties& queue) {
                        return queue.queueCount > 0 &&
                            (queue.queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0;
                    }
                );
                if (hasGraphicsQueue) {
                    supportedVersion = std::max(
                        supportedVersion,
                        std::min(loaderVersion, deviceVersion)
                    );
                }
            }
        }
    }

    vkDestroyInstance(instance, nullptr);
    return supportedVersion;
}

struct ColorFillParams {
    float progress = 0.0F;
    float dimLevel = 0.0F;
    float aspectRatio = 1.0F;
    float reverse = 0.0F;
    float originX = 0.5F;
    float originY = 0.8F;
    float scrollOffsetX = 0.5F;
    float scrollWindowX = 1.0F;
};

static_assert(sizeof(ColorFillParams) == 32);

class VulkanColorFillEngine {
public:
    VulkanColorFillEngine(AAssetManager* assets, bool reverse)
        : assets_(assets) {
        params_.reverse = reverse ? 1.0F : 0.0F;
    }

    ~VulkanColorFillEngine() {
        destroySurface();
    }

    bool setSurface(
        JNIEnv* env,
        jobject javaSurface,
        uint32_t requestedWidth,
        uint32_t requestedHeight
    ) {
        destroySurface();
        window_ = ANativeWindow_fromSurface(env, javaSurface);
        if (window_ == nullptr) {
            logError("ANativeWindow_fromSurface returned null");
            return false;
        }
        requestedWidth_ = requestedWidth;
        requestedHeight_ = requestedHeight;

        if (!createNegotiatedInstanceAndSelectDevice() ||
            !createDevice() ||
            !createSwapchain() ||
            !createDescriptorResources() ||
            !createRenderPass() ||
            !createPipeline() ||
            !createFramebuffers() ||
            !createCommandResources() ||
            !createSyncResources()) {
            destroySurface();
            return false;
        }
        params_.aspectRatio =
            static_cast<float>(extent_.width) /
            static_cast<float>(std::max(1U, extent_.height));
        return true;
    }

    uint32_t apiVersion() const {
        return apiVersion_;
    }

    void destroySurface() {
        if (device_ != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(device_);
            destroyTexture();
            if (imageAvailable_ != VK_NULL_HANDLE) {
                vkDestroySemaphore(device_, imageAvailable_, nullptr);
            }
            for (VkSemaphore semaphore : renderFinishedSemaphores_) {
                if (semaphore != VK_NULL_HANDLE) {
                    vkDestroySemaphore(device_, semaphore, nullptr);
                }
            }
            if (renderFence_ != VK_NULL_HANDLE) {
                vkDestroyFence(device_, renderFence_, nullptr);
            }
            if (commandPool_ != VK_NULL_HANDLE) {
                vkDestroyCommandPool(device_, commandPool_, nullptr);
            }
            for (VkFramebuffer framebuffer : framebuffers_) {
                vkDestroyFramebuffer(device_, framebuffer, nullptr);
            }
            if (pipeline_ != VK_NULL_HANDLE) {
                vkDestroyPipeline(device_, pipeline_, nullptr);
            }
            if (pipelineLayout_ != VK_NULL_HANDLE) {
                vkDestroyPipelineLayout(device_, pipelineLayout_, nullptr);
            }
            if (renderPass_ != VK_NULL_HANDLE) {
                vkDestroyRenderPass(device_, renderPass_, nullptr);
            }
            if (descriptorPool_ != VK_NULL_HANDLE) {
                vkDestroyDescriptorPool(device_, descriptorPool_, nullptr);
            }
            if (descriptorSetLayout_ != VK_NULL_HANDLE) {
                vkDestroyDescriptorSetLayout(
                    device_,
                    descriptorSetLayout_,
                    nullptr
                );
            }
            for (VkImageView imageView : swapchainImageViews_) {
                vkDestroyImageView(device_, imageView, nullptr);
            }
            if (swapchain_ != VK_NULL_HANDLE) {
                vkDestroySwapchainKHR(device_, swapchain_, nullptr);
            }
            vkDestroyDevice(device_, nullptr);
        }
        if (surface_ != VK_NULL_HANDLE && instance_ != VK_NULL_HANDLE) {
            vkDestroySurfaceKHR(instance_, surface_, nullptr);
        }
        if (instance_ != VK_NULL_HANDLE) {
            vkDestroyInstance(instance_, nullptr);
        }
        if (window_ != nullptr) {
            ANativeWindow_release(window_);
        }

        window_ = nullptr;
        instance_ = VK_NULL_HANDLE;
        surface_ = VK_NULL_HANDLE;
        physicalDevice_ = VK_NULL_HANDLE;
        device_ = VK_NULL_HANDLE;
        instanceApiVersion_ = 0;
        apiVersion_ = 0;
        queue_ = VK_NULL_HANDLE;
        swapchain_ = VK_NULL_HANDLE;
        swapchainImages_.clear();
        swapchainImageViews_.clear();
        framebuffers_.clear();
        descriptorSetLayout_ = VK_NULL_HANDLE;
        descriptorPool_ = VK_NULL_HANDLE;
        descriptorSet_ = VK_NULL_HANDLE;
        renderPass_ = VK_NULL_HANDLE;
        pipelineLayout_ = VK_NULL_HANDLE;
        pipeline_ = VK_NULL_HANDLE;
        commandPool_ = VK_NULL_HANDLE;
        commandBuffer_ = VK_NULL_HANDLE;
        imageAvailable_ = VK_NULL_HANDLE;
        renderFinishedSemaphores_.clear();
        renderFence_ = VK_NULL_HANDLE;
        textureReady_ = false;
        extent_ = {};
    }

    bool uploadBitmap(JNIEnv* env, jobject bitmap) {
        if (device_ == VK_NULL_HANDLE || commandPool_ == VK_NULL_HANDLE) {
            return false;
        }

        AndroidBitmapInfo bitmapInfo{};
        if (AndroidBitmap_getInfo(env, bitmap, &bitmapInfo) != ANDROID_BITMAP_RESULT_SUCCESS ||
            bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
            bitmapInfo.width == 0 ||
            bitmapInfo.height == 0) {
            logError("Color Fill requires an RGBA_8888 bitmap");
            return false;
        }

        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(physicalDevice_, &properties);
        if (bitmapInfo.width > properties.limits.maxImageDimension2D ||
            bitmapInfo.height > properties.limits.maxImageDimension2D) {
            logError("Wallpaper texture exceeds maxImageDimension2D");
            return false;
        }

        const VkDeviceSize rowSize =
            static_cast<VkDeviceSize>(bitmapInfo.width) * 4U;
        const VkDeviceSize bufferSize =
            rowSize * static_cast<VkDeviceSize>(bitmapInfo.height);
        VkBuffer stagingBuffer = VK_NULL_HANDLE;
        VkDeviceMemory stagingMemory = VK_NULL_HANDLE;
        if (!createBuffer(
                bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                    VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                stagingBuffer,
                stagingMemory
            )) {
            return false;
        }

        void* sourcePixels = nullptr;
        if (AndroidBitmap_lockPixels(env, bitmap, &sourcePixels) !=
            ANDROID_BITMAP_RESULT_SUCCESS) {
            vkDestroyBuffer(device_, stagingBuffer, nullptr);
            vkFreeMemory(device_, stagingMemory, nullptr);
            return false;
        }

        void* mapped = nullptr;
        bool copied = vkMapMemory(
            device_,
            stagingMemory,
            0,
            bufferSize,
            0,
            &mapped
        ) == VK_SUCCESS;
        if (copied) {
            const auto* source = static_cast<const uint8_t*>(sourcePixels);
            auto* destination = static_cast<uint8_t*>(mapped);
            for (uint32_t row = 0; row < bitmapInfo.height; ++row) {
                std::memcpy(
                    destination + row * rowSize,
                    source + row * bitmapInfo.stride,
                    static_cast<size_t>(rowSize)
                );
            }
            vkUnmapMemory(device_, stagingMemory);
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        if (!copied) {
            vkDestroyBuffer(device_, stagingBuffer, nullptr);
            vkFreeMemory(device_, stagingMemory, nullptr);
            return false;
        }

        vkDeviceWaitIdle(device_);
        destroyTexture();
        textureWidth_ = bitmapInfo.width;
        textureHeight_ = bitmapInfo.height;
        if (!createTextureImage() ||
            !copyBufferToTexture(stagingBuffer) ||
            !createTextureViewAndSampler()) {
            vkDestroyBuffer(device_, stagingBuffer, nullptr);
            vkFreeMemory(device_, stagingMemory, nullptr);
            destroyTexture();
            return false;
        }
        vkDestroyBuffer(device_, stagingBuffer, nullptr);
        vkFreeMemory(device_, stagingMemory, nullptr);

        VkDescriptorImageInfo imageInfo{};
        imageInfo.sampler = textureSampler_;
        imageInfo.imageView = textureView_;
        imageInfo.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

        VkWriteDescriptorSet descriptorWrite{
            VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET
        };
        descriptorWrite.dstSet = descriptorSet_;
        descriptorWrite.dstBinding = 0;
        descriptorWrite.descriptorCount = 1;
        descriptorWrite.descriptorType =
            VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        descriptorWrite.pImageInfo = &imageInfo;
        vkUpdateDescriptorSets(device_, 1, &descriptorWrite, 0, nullptr);
        textureReady_ = true;
        return true;
    }

    void setState(
        float progress,
        float dimLevel,
        float originX,
        float originY,
        float scrollOffsetX,
        float scrollWindowX
    ) {
        params_.progress = progress;
        params_.dimLevel = dimLevel;
        params_.originX = originX;
        params_.originY = originY;
        params_.scrollOffsetX = scrollOffsetX;
        params_.scrollWindowX = scrollWindowX;
    }

    int render() {
        if (device_ == VK_NULL_HANDLE ||
            swapchain_ == VK_NULL_HANDLE ||
            !textureReady_) {
            return -1;
        }
        if (vkWaitForFences(
                device_,
                1,
                &renderFence_,
                VK_TRUE,
                std::numeric_limits<uint64_t>::max()
            ) != VK_SUCCESS) {
            return -1;
        }

        uint32_t imageIndex = 0;
        const VkResult acquireResult = vkAcquireNextImageKHR(
            device_,
            swapchain_,
            std::numeric_limits<uint64_t>::max(),
            imageAvailable_,
            VK_NULL_HANDLE,
            &imageIndex
        );
        if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) return 1;
        if (acquireResult != VK_SUCCESS &&
            acquireResult != VK_SUBOPTIMAL_KHR) {
            return -1;
        }
        if (imageIndex >= framebuffers_.size() ||
            imageIndex >= renderFinishedSemaphores_.size()) {
            return -1;
        }
        const VkSemaphore renderFinished =
            renderFinishedSemaphores_[imageIndex];
        if (renderFinished == VK_NULL_HANDLE) return -1;

        if (vkResetFences(device_, 1, &renderFence_) != VK_SUCCESS ||
            vkResetCommandBuffer(commandBuffer_, 0) != VK_SUCCESS ||
            !recordRenderCommands(imageIndex)) {
            return -1;
        }

        constexpr VkPipelineStageFlags waitStage =
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submitInfo{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submitInfo.waitSemaphoreCount = 1;
        submitInfo.pWaitSemaphores = &imageAvailable_;
        submitInfo.pWaitDstStageMask = &waitStage;
        submitInfo.commandBufferCount = 1;
        submitInfo.pCommandBuffers = &commandBuffer_;
        submitInfo.signalSemaphoreCount = 1;
        submitInfo.pSignalSemaphores = &renderFinished;
        if (vkQueueSubmit(queue_, 1, &submitInfo, renderFence_) != VK_SUCCESS) {
            return -1;
        }

        VkPresentInfoKHR presentInfo{
            VK_STRUCTURE_TYPE_PRESENT_INFO_KHR
        };
        presentInfo.waitSemaphoreCount = 1;
        presentInfo.pWaitSemaphores = &renderFinished;
        presentInfo.swapchainCount = 1;
        presentInfo.pSwapchains = &swapchain_;
        presentInfo.pImageIndices = &imageIndex;
        const VkResult presentResult = vkQueuePresentKHR(queue_, &presentInfo);
        if (presentResult == VK_ERROR_OUT_OF_DATE_KHR) return 1;
        if (presentResult != VK_SUCCESS &&
            presentResult != VK_SUBOPTIMAL_KHR) {
            return -1;
        }
        return 0;
    }

private:
    bool createNegotiatedInstanceAndSelectDevice() {
        const uint32_t loaderVersion = loaderCoreApiVersion();
        if (loaderVersion < VK_API_VERSION_1_1) {
            logError("Vulkan 1.1 loader is unavailable");
            return false;
        }

        if (!createInstance(VK_API_VERSION_1_1) ||
            !createAndroidSurface() ||
            !selectPhysicalDevice(loaderVersion)) {
            destroyNegotiationInstance();
            return false;
        }

        const uint32_t negotiatedVersion = apiVersion_;
        if (negotiatedVersion == VK_API_VERSION_1_1) {
            return true;
        }

        destroyNegotiationInstance();
        if (!createInstance(negotiatedVersion) ||
            !createAndroidSurface() ||
            !selectPhysicalDevice(negotiatedVersion) ||
            apiVersion_ != negotiatedVersion) {
            destroyNegotiationInstance();
            logError("The negotiated Vulkan API version became unavailable");
            return false;
        }
        return true;
    }

    void destroyNegotiationInstance() {
        if (surface_ != VK_NULL_HANDLE && instance_ != VK_NULL_HANDLE) {
            vkDestroySurfaceKHR(instance_, surface_, nullptr);
        }
        if (instance_ != VK_NULL_HANDLE) {
            vkDestroyInstance(instance_, nullptr);
        }
        instance_ = VK_NULL_HANDLE;
        surface_ = VK_NULL_HANDLE;
        physicalDevice_ = VK_NULL_HANDLE;
        instanceApiVersion_ = 0;
        apiVersion_ = 0;
        queueFamily_ = 0;
    }

    bool createInstance(uint32_t requestedApiVersion) {
        const uint32_t loaderVersion = loaderCoreApiVersion();
        if (requestedApiVersion < VK_API_VERSION_1_1 ||
            requestedApiVersion > loaderVersion ||
            supportedCoreApiVersion(requestedApiVersion) != requestedApiVersion) {
            logError("Unsupported Vulkan instance API version requested");
            return false;
        }
        const auto extensions = instanceExtensions();
        if (!hasExtension(extensions, VK_KHR_SURFACE_EXTENSION_NAME) ||
            !hasExtension(extensions, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME)) {
            logError("Required Android Vulkan surface extensions are unavailable");
            return false;
        }
        const std::array<const char*, 2> requiredExtensions{
            VK_KHR_SURFACE_EXTENSION_NAME,
            VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
        };

        VkApplicationInfo applicationInfo{
            VK_STRUCTURE_TYPE_APPLICATION_INFO
        };
        applicationInfo.pApplicationName = "Atmo Engine";
        applicationInfo.applicationVersion = 1;
        applicationInfo.pEngineName = "Atmo Color Fill";
        applicationInfo.engineVersion = 1;
        applicationInfo.apiVersion = requestedApiVersion;

        VkInstanceCreateInfo createInfo{
            VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO
        };
        createInfo.pApplicationInfo = &applicationInfo;
        createInfo.enabledExtensionCount =
            static_cast<uint32_t>(requiredExtensions.size());
        createInfo.ppEnabledExtensionNames = requiredExtensions.data();
        if (vkCreateInstance(&createInfo, nullptr, &instance_) != VK_SUCCESS) {
            logError("vkCreateInstance failed");
            return false;
        }
        instanceApiVersion_ = requestedApiVersion;
        return true;
    }

    bool createAndroidSurface() {
        VkAndroidSurfaceCreateInfoKHR surfaceInfo{
            VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR
        };
        surfaceInfo.window = window_;
        if (vkCreateAndroidSurfaceKHR(
                instance_,
                &surfaceInfo,
                nullptr,
                &surface_
            ) != VK_SUCCESS) {
            logError("vkCreateAndroidSurfaceKHR failed");
            return false;
        }
        return true;
    }

    bool selectPhysicalDevice(uint32_t negotiationCeiling) {
        uint32_t deviceCount = 0;
        if (vkEnumeratePhysicalDevices(
                instance_,
                &deviceCount,
                nullptr
            ) != VK_SUCCESS ||
            deviceCount == 0) {
            logError("No Vulkan physical device is available");
            return false;
        }
        std::vector<VkPhysicalDevice> devices(deviceCount);
        if (vkEnumeratePhysicalDevices(
                instance_,
                &deviceCount,
                devices.data()
            ) != VK_SUCCESS) {
            return false;
        }

        VkPhysicalDevice selectedDevice = VK_NULL_HANDLE;
        uint32_t selectedQueueFamily = 0;
        uint32_t selectedApiVersion = 0;
        uint32_t selectedDeviceScore = 0;

        for (VkPhysicalDevice candidate : devices) {
            VkPhysicalDeviceProperties properties{};
            vkGetPhysicalDeviceProperties(candidate, &properties);
            const uint32_t deviceApiVersion =
                supportedCoreApiVersion(properties.apiVersion);
            if (properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU ||
                deviceApiVersion < VK_API_VERSION_1_1) {
                continue;
            }
            const auto extensions = deviceExtensions(candidate);
            if (!hasExtension(extensions, VK_KHR_SWAPCHAIN_EXTENSION_NAME)) {
                continue;
            }

            uint32_t queueCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(
                candidate,
                &queueCount,
                nullptr
            );
            std::vector<VkQueueFamilyProperties> queues(queueCount);
            vkGetPhysicalDeviceQueueFamilyProperties(
                candidate,
                &queueCount,
                queues.data()
            );
            std::optional<uint32_t> presentQueueFamily;
            for (uint32_t index = 0; index < queueCount; ++index) {
                VkBool32 supportsPresent = VK_FALSE;
                const VkResult supportResult =
                    vkGetPhysicalDeviceSurfaceSupportKHR(
                        candidate,
                        index,
                        surface_,
                        &supportsPresent
                    );
                if (supportResult == VK_SUCCESS &&
                    queues[index].queueCount > 0 &&
                    (queues[index].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0 &&
                    supportsPresent == VK_TRUE) {
                    presentQueueFamily = index;
                    break;
                }
            }
            if (!presentQueueFamily.has_value()) continue;

            const uint32_t candidateApiVersion = std::min(
                negotiationCeiling,
                deviceApiVersion
            );
            const uint32_t deviceScore =
                properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU ? 3U :
                properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU ? 2U :
                1U;
            if (candidateApiVersion > selectedApiVersion ||
                (candidateApiVersion == selectedApiVersion &&
                 deviceScore > selectedDeviceScore)) {
                selectedDevice = candidate;
                selectedQueueFamily = *presentQueueFamily;
                selectedApiVersion = candidateApiVersion;
                selectedDeviceScore = deviceScore;
            }
        }

        if (selectedDevice != VK_NULL_HANDLE &&
            selectedApiVersion >= VK_API_VERSION_1_1) {
            physicalDevice_ = selectedDevice;
            queueFamily_ = selectedQueueFamily;
            apiVersion_ = selectedApiVersion;
            return true;
        }
        logError("No Vulkan queue can render and present the wallpaper surface");
        return false;
    }

    bool createDevice() {
        if (apiVersion_ < VK_API_VERSION_1_1 ||
            apiVersion_ != instanceApiVersion_) {
            logError("Vulkan device creation attempted before API negotiation");
            return false;
        }
        constexpr float priority = 1.0F;
        VkDeviceQueueCreateInfo queueInfo{
            VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO
        };
        queueInfo.queueFamilyIndex = queueFamily_;
        queueInfo.queueCount = 1;
        queueInfo.pQueuePriorities = &priority;

        constexpr const char* requiredExtension =
            VK_KHR_SWAPCHAIN_EXTENSION_NAME;
        VkDeviceCreateInfo deviceInfo{
            VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO
        };
        deviceInfo.queueCreateInfoCount = 1;
        deviceInfo.pQueueCreateInfos = &queueInfo;
        deviceInfo.enabledExtensionCount = 1;
        deviceInfo.ppEnabledExtensionNames = &requiredExtension;
        if (vkCreateDevice(
                physicalDevice_,
                &deviceInfo,
                nullptr,
                &device_
            ) != VK_SUCCESS) {
            logError("vkCreateDevice failed");
            return false;
        }
        vkGetDeviceQueue(device_, queueFamily_, 0, &queue_);
        return queue_ != VK_NULL_HANDLE;
    }

    bool createSwapchain() {
        VkSurfaceCapabilitiesKHR capabilities{};
        if (vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                physicalDevice_,
                surface_,
                &capabilities
            ) != VK_SUCCESS) {
            return false;
        }

        uint32_t formatCount = 0;
        if (vkGetPhysicalDeviceSurfaceFormatsKHR(
                physicalDevice_,
                surface_,
                &formatCount,
                nullptr
            ) != VK_SUCCESS ||
            formatCount == 0) {
            return false;
        }
        std::vector<VkSurfaceFormatKHR> formats(formatCount);
        if (vkGetPhysicalDeviceSurfaceFormatsKHR(
                physicalDevice_,
                surface_,
                &formatCount,
                formats.data()
            ) != VK_SUCCESS) {
            return false;
        }
        auto preferred = std::find_if(
            formats.begin(),
            formats.end(),
            [](const VkSurfaceFormatKHR& format) {
                return (
                    format.format == VK_FORMAT_R8G8B8A8_UNORM ||
                    format.format == VK_FORMAT_B8G8R8A8_UNORM
                ) && format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
            }
        );
        const VkSurfaceFormatKHR selectedFormat =
            preferred != formats.end() ? *preferred : formats.front();
        swapchainFormat_ = selectedFormat.format;

        if (capabilities.currentExtent.width !=
            std::numeric_limits<uint32_t>::max()) {
            extent_ = capabilities.currentExtent;
        } else {
            extent_.width = std::clamp(
                requestedWidth_,
                capabilities.minImageExtent.width,
                capabilities.maxImageExtent.width
            );
            extent_.height = std::clamp(
                requestedHeight_,
                capabilities.minImageExtent.height,
                capabilities.maxImageExtent.height
            );
        }
        if (extent_.width == 0 || extent_.height == 0) return false;

        uint32_t imageCount = capabilities.minImageCount + 1;
        if (capabilities.maxImageCount > 0) {
            imageCount = std::min(imageCount, capabilities.maxImageCount);
        }
        VkCompositeAlphaFlagBitsKHR compositeAlpha =
            VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        const std::array<VkCompositeAlphaFlagBitsKHR, 4> alphaModes{
            VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
            VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
            VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR
        };
        for (VkCompositeAlphaFlagBitsKHR mode : alphaModes) {
            if ((capabilities.supportedCompositeAlpha & mode) != 0) {
                compositeAlpha = mode;
                break;
            }
        }

        VkSwapchainCreateInfoKHR swapchainInfo{
            VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR
        };
        swapchainInfo.surface = surface_;
        swapchainInfo.minImageCount = imageCount;
        swapchainInfo.imageFormat = selectedFormat.format;
        swapchainInfo.imageColorSpace = selectedFormat.colorSpace;
        swapchainInfo.imageExtent = extent_;
        swapchainInfo.imageArrayLayers = 1;
        swapchainInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        swapchainInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        swapchainInfo.preTransform = capabilities.currentTransform;
        swapchainInfo.compositeAlpha = compositeAlpha;
        swapchainInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        swapchainInfo.clipped = VK_TRUE;
        if (vkCreateSwapchainKHR(
                device_,
                &swapchainInfo,
                nullptr,
                &swapchain_
            ) != VK_SUCCESS) {
            logError("vkCreateSwapchainKHR failed");
            return false;
        }

        uint32_t actualImageCount = 0;
        vkGetSwapchainImagesKHR(
            device_,
            swapchain_,
            &actualImageCount,
            nullptr
        );
        swapchainImages_.resize(actualImageCount);
        if (vkGetSwapchainImagesKHR(
                device_,
                swapchain_,
                &actualImageCount,
                swapchainImages_.data()
            ) != VK_SUCCESS) {
            return false;
        }

        swapchainImageViews_.reserve(actualImageCount);
        for (VkImage image : swapchainImages_) {
            VkImageViewCreateInfo viewInfo{
                VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO
            };
            viewInfo.image = image;
            viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
            viewInfo.format = swapchainFormat_;
            viewInfo.subresourceRange.aspectMask =
                VK_IMAGE_ASPECT_COLOR_BIT;
            viewInfo.subresourceRange.levelCount = 1;
            viewInfo.subresourceRange.layerCount = 1;
            VkImageView view = VK_NULL_HANDLE;
            if (vkCreateImageView(
                    device_,
                    &viewInfo,
                    nullptr,
                    &view
                ) != VK_SUCCESS) {
                return false;
            }
            swapchainImageViews_.push_back(view);
        }
        return !swapchainImageViews_.empty();
    }

    bool createDescriptorResources() {
        VkDescriptorSetLayoutBinding binding{};
        binding.binding = 0;
        binding.descriptorType =
            VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        binding.descriptorCount = 1;
        binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;

        VkDescriptorSetLayoutCreateInfo layoutInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO
        };
        layoutInfo.bindingCount = 1;
        layoutInfo.pBindings = &binding;
        if (vkCreateDescriptorSetLayout(
                device_,
                &layoutInfo,
                nullptr,
                &descriptorSetLayout_
            ) != VK_SUCCESS) {
            return false;
        }

        VkDescriptorPoolSize poolSize{};
        poolSize.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        poolSize.descriptorCount = 1;
        VkDescriptorPoolCreateInfo poolInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO
        };
        poolInfo.maxSets = 1;
        poolInfo.poolSizeCount = 1;
        poolInfo.pPoolSizes = &poolSize;
        if (vkCreateDescriptorPool(
                device_,
                &poolInfo,
                nullptr,
                &descriptorPool_
            ) != VK_SUCCESS) {
            return false;
        }

        VkDescriptorSetAllocateInfo allocateInfo{
            VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO
        };
        allocateInfo.descriptorPool = descriptorPool_;
        allocateInfo.descriptorSetCount = 1;
        allocateInfo.pSetLayouts = &descriptorSetLayout_;
        return vkAllocateDescriptorSets(
            device_,
            &allocateInfo,
            &descriptorSet_
        ) == VK_SUCCESS;
    }

    bool createRenderPass() {
        VkAttachmentDescription attachment{};
        attachment.format = swapchainFormat_;
        attachment.samples = VK_SAMPLE_COUNT_1_BIT;
        attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        attachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        attachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        attachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

        VkAttachmentReference colorReference{};
        colorReference.attachment = 0;
        colorReference.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &colorReference;

        VkSubpassDependency dependency{};
        dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
        dependency.dstSubpass = 0;
        dependency.srcStageMask =
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstStageMask =
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstAccessMask =
            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        VkRenderPassCreateInfo renderPassInfo{
            VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO
        };
        renderPassInfo.attachmentCount = 1;
        renderPassInfo.pAttachments = &attachment;
        renderPassInfo.subpassCount = 1;
        renderPassInfo.pSubpasses = &subpass;
        renderPassInfo.dependencyCount = 1;
        renderPassInfo.pDependencies = &dependency;
        return vkCreateRenderPass(
            device_,
            &renderPassInfo,
            nullptr,
            &renderPass_
        ) == VK_SUCCESS;
    }

    std::vector<uint32_t> readShaderAsset(const char* path) const {
        if (assets_ == nullptr) return {};
        AAsset* asset = AAssetManager_open(
            assets_,
            path,
            AASSET_MODE_STREAMING
        );
        if (asset == nullptr) return {};
        const off_t length = AAsset_getLength(asset);
        if (length <= 0 || length % 4 != 0) {
            AAsset_close(asset);
            return {};
        }
        std::vector<uint32_t> code(
            static_cast<size_t>(length) / sizeof(uint32_t)
        );
        const int64_t read = AAsset_read(
            asset,
            code.data(),
            static_cast<size_t>(length)
        );
        AAsset_close(asset);
        if (read != length) return {};
        return code;
    }

    VkShaderModule createShaderModule(
        const std::vector<uint32_t>& code
    ) const {
        if (code.empty()) return VK_NULL_HANDLE;
        VkShaderModuleCreateInfo moduleInfo{
            VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO
        };
        moduleInfo.codeSize = code.size() * sizeof(uint32_t);
        moduleInfo.pCode = code.data();
        VkShaderModule module = VK_NULL_HANDLE;
        if (vkCreateShaderModule(
                device_,
                &moduleInfo,
                nullptr,
                &module
            ) != VK_SUCCESS) {
            return VK_NULL_HANDLE;
        }
        return module;
    }

    bool createPipeline() {
        const auto vertexCode = readShaderAsset(kVertexShader);
        const auto fragmentCode = readShaderAsset(kFragmentShader);
        const VkShaderModule vertexModule =
            createShaderModule(vertexCode);
        const VkShaderModule fragmentModule =
            createShaderModule(fragmentCode);
        if (vertexModule == VK_NULL_HANDLE ||
            fragmentModule == VK_NULL_HANDLE) {
            if (vertexModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device_, vertexModule, nullptr);
            }
            if (fragmentModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device_, fragmentModule, nullptr);
            }
            logError("Unable to load Color Fill SPIR-V shaders");
            return false;
        }

        const std::array<VkPipelineShaderStageCreateInfo, 2> stages{{
            {
                VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                nullptr,
                0,
                VK_SHADER_STAGE_VERTEX_BIT,
                vertexModule,
                "main",
                nullptr
            },
            {
                VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                nullptr,
                0,
                VK_SHADER_STAGE_FRAGMENT_BIT,
                fragmentModule,
                "main",
                nullptr
            }
        }};

        VkPipelineVertexInputStateCreateInfo vertexInput{
            VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
        };
        VkPipelineInputAssemblyStateCreateInfo inputAssembly{
            VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO
        };
        inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

        VkViewport viewport{};
        viewport.width = static_cast<float>(extent_.width);
        viewport.height = static_cast<float>(extent_.height);
        viewport.minDepth = 0.0F;
        viewport.maxDepth = 1.0F;
        VkRect2D scissor{{0, 0}, extent_};
        VkPipelineViewportStateCreateInfo viewportState{
            VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO
        };
        viewportState.viewportCount = 1;
        viewportState.pViewports = &viewport;
        viewportState.scissorCount = 1;
        viewportState.pScissors = &scissor;

        VkPipelineRasterizationStateCreateInfo rasterizer{
            VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO
        };
        rasterizer.polygonMode = VK_POLYGON_MODE_FILL;
        rasterizer.cullMode = VK_CULL_MODE_NONE;
        rasterizer.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        rasterizer.lineWidth = 1.0F;

        VkPipelineMultisampleStateCreateInfo multisampling{
            VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO
        };
        multisampling.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

        VkPipelineColorBlendAttachmentState colorBlendAttachment{};
        colorBlendAttachment.colorWriteMask =
            VK_COLOR_COMPONENT_R_BIT |
            VK_COLOR_COMPONENT_G_BIT |
            VK_COLOR_COMPONENT_B_BIT |
            VK_COLOR_COMPONENT_A_BIT;
        VkPipelineColorBlendStateCreateInfo colorBlending{
            VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO
        };
        colorBlending.attachmentCount = 1;
        colorBlending.pAttachments = &colorBlendAttachment;

        VkPushConstantRange pushConstants{};
        pushConstants.stageFlags =
            VK_SHADER_STAGE_VERTEX_BIT |
            VK_SHADER_STAGE_FRAGMENT_BIT;
        pushConstants.size = sizeof(ColorFillParams);
        VkPipelineLayoutCreateInfo pipelineLayoutInfo{
            VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO
        };
        pipelineLayoutInfo.setLayoutCount = 1;
        pipelineLayoutInfo.pSetLayouts = &descriptorSetLayout_;
        pipelineLayoutInfo.pushConstantRangeCount = 1;
        pipelineLayoutInfo.pPushConstantRanges = &pushConstants;
        if (vkCreatePipelineLayout(
                device_,
                &pipelineLayoutInfo,
                nullptr,
                &pipelineLayout_
            ) != VK_SUCCESS) {
            vkDestroyShaderModule(device_, vertexModule, nullptr);
            vkDestroyShaderModule(device_, fragmentModule, nullptr);
            return false;
        }

        VkGraphicsPipelineCreateInfo pipelineInfo{
            VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO
        };
        pipelineInfo.stageCount =
            static_cast<uint32_t>(stages.size());
        pipelineInfo.pStages = stages.data();
        pipelineInfo.pVertexInputState = &vertexInput;
        pipelineInfo.pInputAssemblyState = &inputAssembly;
        pipelineInfo.pViewportState = &viewportState;
        pipelineInfo.pRasterizationState = &rasterizer;
        pipelineInfo.pMultisampleState = &multisampling;
        pipelineInfo.pColorBlendState = &colorBlending;
        pipelineInfo.layout = pipelineLayout_;
        pipelineInfo.renderPass = renderPass_;
        pipelineInfo.subpass = 0;
        const VkResult result = vkCreateGraphicsPipelines(
            device_,
            VK_NULL_HANDLE,
            1,
            &pipelineInfo,
            nullptr,
            &pipeline_
        );
        vkDestroyShaderModule(device_, vertexModule, nullptr);
        vkDestroyShaderModule(device_, fragmentModule, nullptr);
        return result == VK_SUCCESS;
    }

    bool createFramebuffers() {
        framebuffers_.reserve(swapchainImageViews_.size());
        for (VkImageView imageView : swapchainImageViews_) {
            VkFramebufferCreateInfo framebufferInfo{
                VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO
            };
            framebufferInfo.renderPass = renderPass_;
            framebufferInfo.attachmentCount = 1;
            framebufferInfo.pAttachments = &imageView;
            framebufferInfo.width = extent_.width;
            framebufferInfo.height = extent_.height;
            framebufferInfo.layers = 1;
            VkFramebuffer framebuffer = VK_NULL_HANDLE;
            if (vkCreateFramebuffer(
                    device_,
                    &framebufferInfo,
                    nullptr,
                    &framebuffer
                ) != VK_SUCCESS) {
                return false;
            }
            framebuffers_.push_back(framebuffer);
        }
        return !framebuffers_.empty();
    }

    bool createCommandResources() {
        VkCommandPoolCreateInfo poolInfo{
            VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO
        };
        poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        poolInfo.queueFamilyIndex = queueFamily_;
        if (vkCreateCommandPool(
                device_,
                &poolInfo,
                nullptr,
                &commandPool_
            ) != VK_SUCCESS) {
            return false;
        }

        VkCommandBufferAllocateInfo allocateInfo{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO
        };
        allocateInfo.commandPool = commandPool_;
        allocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        allocateInfo.commandBufferCount = 1;
        return vkAllocateCommandBuffers(
            device_,
            &allocateInfo,
            &commandBuffer_
        ) == VK_SUCCESS;
    }

    bool createSyncResources() {
        VkSemaphoreCreateInfo semaphoreInfo{
            VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO
        };
        VkFenceCreateInfo fenceInfo{
            VK_STRUCTURE_TYPE_FENCE_CREATE_INFO
        };
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        if (vkCreateSemaphore(
                device_,
                &semaphoreInfo,
                nullptr,
                &imageAvailable_
            ) != VK_SUCCESS) {
            return false;
        }

        renderFinishedSemaphores_.assign(
            swapchainImages_.size(),
            VK_NULL_HANDLE
        );
        for (VkSemaphore& semaphore : renderFinishedSemaphores_) {
            if (vkCreateSemaphore(
                    device_,
                    &semaphoreInfo,
                    nullptr,
                    &semaphore
                ) != VK_SUCCESS) {
                return false;
            }
        }

        return !renderFinishedSemaphores_.empty() &&
            vkCreateFence(
                device_,
                &fenceInfo,
                nullptr,
                &renderFence_
            ) == VK_SUCCESS;
    }

    std::optional<uint32_t> findMemoryType(
        uint32_t allowedTypes,
        VkMemoryPropertyFlags requiredProperties
    ) const {
        VkPhysicalDeviceMemoryProperties properties{};
        vkGetPhysicalDeviceMemoryProperties(
            physicalDevice_,
            &properties
        );
        for (uint32_t index = 0;
             index < properties.memoryTypeCount;
             ++index) {
            if ((allowedTypes & (1U << index)) != 0 &&
                (properties.memoryTypes[index].propertyFlags &
                    requiredProperties) == requiredProperties) {
                return index;
            }
        }
        return std::nullopt;
    }

    bool createBuffer(
        VkDeviceSize size,
        VkBufferUsageFlags usage,
        VkMemoryPropertyFlags memoryProperties,
        VkBuffer& buffer,
        VkDeviceMemory& memory
    ) {
        VkBufferCreateInfo bufferInfo{
            VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO
        };
        bufferInfo.size = size;
        bufferInfo.usage = usage;
        bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(
                device_,
                &bufferInfo,
                nullptr,
                &buffer
            ) != VK_SUCCESS) {
            return false;
        }

        VkMemoryRequirements requirements{};
        vkGetBufferMemoryRequirements(device_, buffer, &requirements);
        const auto memoryType = findMemoryType(
            requirements.memoryTypeBits,
            memoryProperties
        );
        if (!memoryType.has_value()) {
            vkDestroyBuffer(device_, buffer, nullptr);
            buffer = VK_NULL_HANDLE;
            return false;
        }
        VkMemoryAllocateInfo allocation{
            VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
        };
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = *memoryType;
        if (vkAllocateMemory(
                device_,
                &allocation,
                nullptr,
                &memory
            ) != VK_SUCCESS) {
            vkDestroyBuffer(device_, buffer, nullptr);
            buffer = VK_NULL_HANDLE;
            return false;
        }
        if (vkBindBufferMemory(device_, buffer, memory, 0) != VK_SUCCESS) {
            vkDestroyBuffer(device_, buffer, nullptr);
            vkFreeMemory(device_, memory, nullptr);
            buffer = VK_NULL_HANDLE;
            memory = VK_NULL_HANDLE;
            return false;
        }
        return true;
    }

    bool createTextureImage() {
        VkImageCreateInfo imageInfo{
            VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO
        };
        imageInfo.imageType = VK_IMAGE_TYPE_2D;
        imageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
        imageInfo.extent = {textureWidth_, textureHeight_, 1};
        imageInfo.mipLevels = 1;
        imageInfo.arrayLayers = 1;
        imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
        imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
        imageInfo.usage =
            VK_IMAGE_USAGE_TRANSFER_DST_BIT |
            VK_IMAGE_USAGE_SAMPLED_BIT;
        imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        if (vkCreateImage(
                device_,
                &imageInfo,
                nullptr,
                &textureImage_
            ) != VK_SUCCESS) {
            return false;
        }

        VkMemoryRequirements requirements{};
        vkGetImageMemoryRequirements(
            device_,
            textureImage_,
            &requirements
        );
        const auto memoryType = findMemoryType(
            requirements.memoryTypeBits,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
        );
        if (!memoryType.has_value()) return false;
        VkMemoryAllocateInfo allocation{
            VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
        };
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = *memoryType;
        if (vkAllocateMemory(
                device_,
                &allocation,
                nullptr,
                &textureMemory_
            ) != VK_SUCCESS) {
            return false;
        }
        return vkBindImageMemory(
            device_,
            textureImage_,
            textureMemory_,
            0
        ) == VK_SUCCESS;
    }

    VkCommandBuffer beginSingleUseCommands() {
        VkCommandBuffer command = VK_NULL_HANDLE;
        VkCommandBufferAllocateInfo allocation{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO
        };
        allocation.commandPool = commandPool_;
        allocation.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        allocation.commandBufferCount = 1;
        if (vkAllocateCommandBuffers(
                device_,
                &allocation,
                &command
            ) != VK_SUCCESS) {
            return VK_NULL_HANDLE;
        }
        VkCommandBufferBeginInfo beginInfo{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
        };
        beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        if (vkBeginCommandBuffer(command, &beginInfo) != VK_SUCCESS) {
            vkFreeCommandBuffers(device_, commandPool_, 1, &command);
            return VK_NULL_HANDLE;
        }
        return command;
    }

    bool finishSingleUseCommands(VkCommandBuffer command) {
        if (vkEndCommandBuffer(command) != VK_SUCCESS) return false;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &command;
        const bool success =
            vkQueueSubmit(queue_, 1, &submit, VK_NULL_HANDLE) == VK_SUCCESS &&
            vkQueueWaitIdle(queue_) == VK_SUCCESS;
        vkFreeCommandBuffers(device_, commandPool_, 1, &command);
        return success;
    }

    bool copyBufferToTexture(VkBuffer source) {
        VkCommandBuffer command = beginSingleUseCommands();
        if (command == VK_NULL_HANDLE) return false;

        VkImageMemoryBarrier toTransfer{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER
        };
        toTransfer.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        toTransfer.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        toTransfer.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toTransfer.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toTransfer.image = textureImage_;
        toTransfer.subresourceRange.aspectMask =
            VK_IMAGE_ASPECT_COLOR_BIT;
        toTransfer.subresourceRange.levelCount = 1;
        toTransfer.subresourceRange.layerCount = 1;
        toTransfer.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        vkCmdPipelineBarrier(
            command,
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT,
            0,
            0,
            nullptr,
            0,
            nullptr,
            1,
            &toTransfer
        );

        VkBufferImageCopy copy{};
        copy.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        copy.imageSubresource.layerCount = 1;
        copy.imageExtent = {textureWidth_, textureHeight_, 1};
        vkCmdCopyBufferToImage(
            command,
            source,
            textureImage_,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            1,
            &copy
        );

        VkImageMemoryBarrier toShader{
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER
        };
        toShader.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        toShader.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        toShader.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toShader.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        toShader.image = textureImage_;
        toShader.subresourceRange.aspectMask =
            VK_IMAGE_ASPECT_COLOR_BIT;
        toShader.subresourceRange.levelCount = 1;
        toShader.subresourceRange.layerCount = 1;
        toShader.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        toShader.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        vkCmdPipelineBarrier(
            command,
            VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0,
            0,
            nullptr,
            0,
            nullptr,
            1,
            &toShader
        );
        return finishSingleUseCommands(command);
    }

    bool createTextureViewAndSampler() {
        VkImageViewCreateInfo viewInfo{
            VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO
        };
        viewInfo.image = textureImage_;
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
        viewInfo.subresourceRange.aspectMask =
            VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.layerCount = 1;
        if (vkCreateImageView(
                device_,
                &viewInfo,
                nullptr,
                &textureView_
            ) != VK_SUCCESS) {
            return false;
        }

        VkSamplerCreateInfo samplerInfo{
            VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO
        };
        samplerInfo.magFilter = VK_FILTER_LINEAR;
        samplerInfo.minFilter = VK_FILTER_LINEAR;
        samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;
        samplerInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        samplerInfo.maxLod = 0.0F;
        return vkCreateSampler(
            device_,
            &samplerInfo,
            nullptr,
            &textureSampler_
        ) == VK_SUCCESS;
    }

    void destroyTexture() {
        textureReady_ = false;
        if (device_ == VK_NULL_HANDLE) return;
        if (textureSampler_ != VK_NULL_HANDLE) {
            vkDestroySampler(device_, textureSampler_, nullptr);
        }
        if (textureView_ != VK_NULL_HANDLE) {
            vkDestroyImageView(device_, textureView_, nullptr);
        }
        if (textureImage_ != VK_NULL_HANDLE) {
            vkDestroyImage(device_, textureImage_, nullptr);
        }
        if (textureMemory_ != VK_NULL_HANDLE) {
            vkFreeMemory(device_, textureMemory_, nullptr);
        }
        textureSampler_ = VK_NULL_HANDLE;
        textureView_ = VK_NULL_HANDLE;
        textureImage_ = VK_NULL_HANDLE;
        textureMemory_ = VK_NULL_HANDLE;
        textureWidth_ = 0;
        textureHeight_ = 0;
    }

    bool recordRenderCommands(uint32_t imageIndex) {
        VkCommandBufferBeginInfo beginInfo{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
        };
        if (vkBeginCommandBuffer(
                commandBuffer_,
                &beginInfo
            ) != VK_SUCCESS) {
            return false;
        }
        VkClearValue clearColor{};
        clearColor.color = {{0.0F, 0.0F, 0.0F, 1.0F}};
        VkRenderPassBeginInfo renderPassInfo{
            VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
        };
        renderPassInfo.renderPass = renderPass_;
        renderPassInfo.framebuffer = framebuffers_[imageIndex];
        renderPassInfo.renderArea.extent = extent_;
        renderPassInfo.clearValueCount = 1;
        renderPassInfo.pClearValues = &clearColor;
        vkCmdBeginRenderPass(
            commandBuffer_,
            &renderPassInfo,
            VK_SUBPASS_CONTENTS_INLINE
        );
        vkCmdBindPipeline(
            commandBuffer_,
            VK_PIPELINE_BIND_POINT_GRAPHICS,
            pipeline_
        );
        vkCmdBindDescriptorSets(
            commandBuffer_,
            VK_PIPELINE_BIND_POINT_GRAPHICS,
            pipelineLayout_,
            0,
            1,
            &descriptorSet_,
            0,
            nullptr
        );
        vkCmdPushConstants(
            commandBuffer_,
            pipelineLayout_,
            VK_SHADER_STAGE_VERTEX_BIT |
                VK_SHADER_STAGE_FRAGMENT_BIT,
            0,
            sizeof(ColorFillParams),
            &params_
        );
        vkCmdDraw(commandBuffer_, 3, 1, 0, 0);
        vkCmdEndRenderPass(commandBuffer_);
        return vkEndCommandBuffer(commandBuffer_) == VK_SUCCESS;
    }

    AAssetManager* assets_ = nullptr;
    ANativeWindow* window_ = nullptr;
    uint32_t requestedWidth_ = 0;
    uint32_t requestedHeight_ = 0;
    VkInstance instance_ = VK_NULL_HANDLE;
    VkSurfaceKHR surface_ = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice_ = VK_NULL_HANDLE;
    uint32_t instanceApiVersion_ = 0;
    uint32_t apiVersion_ = 0;
    VkDevice device_ = VK_NULL_HANDLE;
    uint32_t queueFamily_ = 0;
    VkQueue queue_ = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain_ = VK_NULL_HANDLE;
    VkFormat swapchainFormat_ = VK_FORMAT_UNDEFINED;
    VkExtent2D extent_{};
    std::vector<VkImage> swapchainImages_;
    std::vector<VkImageView> swapchainImageViews_;
    VkDescriptorSetLayout descriptorSetLayout_ = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool_ = VK_NULL_HANDLE;
    VkDescriptorSet descriptorSet_ = VK_NULL_HANDLE;
    VkRenderPass renderPass_ = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout_ = VK_NULL_HANDLE;
    VkPipeline pipeline_ = VK_NULL_HANDLE;
    std::vector<VkFramebuffer> framebuffers_;
    VkCommandPool commandPool_ = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffer_ = VK_NULL_HANDLE;
    VkSemaphore imageAvailable_ = VK_NULL_HANDLE;
    std::vector<VkSemaphore> renderFinishedSemaphores_;
    VkFence renderFence_ = VK_NULL_HANDLE;
    VkImage textureImage_ = VK_NULL_HANDLE;
    VkDeviceMemory textureMemory_ = VK_NULL_HANDLE;
    VkImageView textureView_ = VK_NULL_HANDLE;
    VkSampler textureSampler_ = VK_NULL_HANDLE;
    uint32_t textureWidth_ = 0;
    uint32_t textureHeight_ = 0;
    bool textureReady_ = false;
    ColorFillParams params_{};
};

VulkanColorFillEngine* engineFromHandle(jlong handle) {
    return reinterpret_cast<VulkanColorFillEngine*>(
        static_cast<intptr_t>(handle)
    );
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeProbe(
    JNIEnv*,
    jobject
) {
    return static_cast<jint>(probeVulkanRuntime());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeCreate(
    JNIEnv* env,
    jobject,
    jobject assetManager,
    jboolean reverse
) {
    AAssetManager* assets = AAssetManager_fromJava(env, assetManager);
    if (assets == nullptr) return 0;
    auto* engine = new VulkanColorFillEngine(
        assets,
        reverse == JNI_TRUE
    );
    return static_cast<jlong>(reinterpret_cast<intptr_t>(engine));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeSetSurface(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject surface,
    jint width,
    jint height
) {
    VulkanColorFillEngine* engine = engineFromHandle(handle);
    if (engine == nullptr || surface == nullptr || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }
    return engine->setSurface(
        env,
        surface,
        static_cast<uint32_t>(width),
        static_cast<uint32_t>(height)
    ) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeGetApiVersion(
    JNIEnv*,
    jobject,
    jlong handle
) {
    VulkanColorFillEngine* engine = engineFromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jint>(engine->apiVersion());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeUploadBitmap(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject bitmap
) {
    VulkanColorFillEngine* engine = engineFromHandle(handle);
    if (engine == nullptr || bitmap == nullptr) return JNI_FALSE;
    return engine->uploadBitmap(env, bitmap) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeSetState(
    JNIEnv*,
    jobject,
    jlong handle,
    jfloat progress,
    jfloat dimLevel,
    jfloat originX,
    jfloat originY,
    jfloat scrollOffsetX,
    jfloat scrollWindowX
) {
    VulkanColorFillEngine* engine = engineFromHandle(handle);
    if (engine == nullptr) return;
    engine->setState(
        progress,
        dimLevel,
        originX,
        originY,
        scrollOffsetX,
        scrollWindowX
    );
}

extern "C" JNIEXPORT jint JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeRender(
    JNIEnv*,
    jobject,
    jlong handle
) {
    VulkanColorFillEngine* engine = engineFromHandle(handle);
    return engine == nullptr ? -1 : engine->render();
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeDestroySurface(
    JNIEnv*,
    jobject,
    jlong handle
) {
    VulkanColorFillEngine* engine = engineFromHandle(handle);
    if (engine != nullptr) engine->destroySurface();
}

extern "C" JNIEXPORT void JNICALL
Java_com_app_nosatmosphereeffect_renderer_vulkan_VulkanNative_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    delete engineFromHandle(handle);
}
