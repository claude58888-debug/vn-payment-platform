package com.vnpay.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vnpay.platform.common.ApiResponse;
import com.vnpay.platform.common.SnowflakeIdGenerator;
import com.vnpay.platform.entity.DeviceBinding;
import com.vnpay.platform.mapper.DeviceBindingMapper;
import com.vnpay.platform.service.DeviceMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceBindingMapper deviceBindingMapper;
    private final DeviceMatchService deviceMatchService;
    private final SnowflakeIdGenerator idGenerator;

    /**
     * POST /api/device/bind
     * Bind a new device to a merchant for notification listening.
     */
    @PostMapping("/bind")
    public ApiResponse<DeviceBinding> bindDevice(@RequestBody Map<String, Object> params) {
        String merchantId = (String) params.get("merchantId");
        String deviceToken = (String) params.get("deviceToken");
        String deviceInfo = params.get("deviceInfo") != null ? params.get("deviceInfo").toString() : null;

        if (merchantId == null || deviceToken == null) {
            return ApiResponse.badRequest("merchantId and deviceToken are required");
        }

        String deviceId = "DEV" + idGenerator.nextStringId();
        String authKey = UUID.randomUUID().toString().replace("-", "");

        DeviceBinding binding = new DeviceBinding();
        binding.setMerchantId(merchantId);
        binding.setDeviceId(deviceId);
        binding.setDeviceToken(deviceToken);
        binding.setAuthKey(authKey);
        binding.setDeviceInfo(deviceInfo);
        binding.setTrustLevel("NEW");
        binding.setMatchAccuracy(BigDecimal.ZERO);
        binding.setStatus("ACTIVE");

        deviceBindingMapper.insert(binding);

        log.info("Device bound: deviceId={}, merchantId={}", deviceId, merchantId);
        return ApiResponse.success(binding);
    }

    /**
     * POST /api/device/unbind
     * Unbind (deactivate) a device.
     */
    @PostMapping("/unbind")
    public ApiResponse<Void> unbindDevice(@RequestBody Map<String, Object> params) {
        String deviceId = (String) params.get("deviceId");
        String merchantId = (String) params.get("merchantId");

        if (deviceId == null || merchantId == null) {
            return ApiResponse.badRequest("deviceId and merchantId are required");
        }

        int updated = deviceBindingMapper.update(null,
                new LambdaUpdateWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getDeviceId, deviceId)
                        .eq(DeviceBinding::getMerchantId, merchantId)
                        .set(DeviceBinding::getStatus, "INACTIVE"));

        if (updated == 0) {
            return ApiResponse.notFound("Device binding not found");
        }

        log.info("Device unbound: deviceId={}, merchantId={}", deviceId, merchantId);
        return ApiResponse.success();
    }

    /**
     * POST /api/device/notification
     * Receive notification data from the Android assistant app.
     */
    @PostMapping("/notification")
    public ApiResponse<Void> receiveNotification(@RequestBody Map<String, Object> params) {
        String deviceId = (String) params.get("deviceId");
        String merchantId = (String) params.get("merchantId");

        if (deviceId == null || merchantId == null) {
            return ApiResponse.badRequest("deviceId and merchantId are required");
        }

        // Verify device is active
        DeviceBinding binding = deviceBindingMapper.selectOne(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getDeviceId, deviceId)
                        .eq(DeviceBinding::getMerchantId, merchantId)
                        .eq(DeviceBinding::getStatus, "ACTIVE"));

        if (binding == null) {
            return ApiResponse.error(403, "Device not bound or not active");
        }

        // Process notification asynchronously
        deviceMatchService.processNotification(params);

        log.info("Device notification received: deviceId={}, merchantId={}", deviceId, merchantId);
        return ApiResponse.success();
    }
}
