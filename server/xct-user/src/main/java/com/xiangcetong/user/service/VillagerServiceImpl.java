package com.xiangcetong.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.Role;
import com.xiangcetong.common.AppProperties;
import com.xiangcetong.user.dto.ProfileUpdateRequest;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.SysUserMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 村民个人信息读写实现（含精细画像字段；PUT 增量更新，Integer 的 0 也写入）。
 */
@Service
public class VillagerServiceImpl implements VillagerService {

    private final SysUserMapper sysUserMapper;
    private final VillagerInfoMapper villagerInfoMapper;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public VillagerServiceImpl(SysUserMapper sysUserMapper,
                               VillagerInfoMapper villagerInfoMapper,
                               StringRedisTemplate redisTemplate,
                               AppProperties appProperties,
                               ObjectMapper objectMapper) {
        this.sysUserMapper = sysUserMapper;
        this.villagerInfoMapper = villagerInfoMapper;
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    /** 查询当前村民资料 */
    @Override
    public Map<String, Object> getProfile() {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        return buildProfile(login.getUserId());
    }

    /** 更新资料；村名变更时同步 sys_user.village_code */
    @Transactional
    @Override
    public Map<String, Object> updateProfile(ProfileUpdateRequest req) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        if (req == null || !req.hasAnyField()) {
            throw BizException.badParam("请至少提交一个要修改的字段");
        }
        Long userId = login.getUserId();
        VillagerInfo existing = villagerInfoMapper.findByUserId(userId);
        if (existing == null) {
            // 首次完善信息：插入一条（未传字段保持 null）
            VillagerInfo info = new VillagerInfo();
            info.setUserId(userId);
            applyRequest(info, req, true);
            villagerInfoMapper.insert(info);
        } else {
            // 增量：仅把请求里非 null 的字段写到实体，再交给 Mapper 按 != null 更新
            applyRequest(existing, req, false);
            villagerInfoMapper.update(existing);
        }
        // 村名变化 → 同步账号上的 village_code，供数据权限过滤
        if (req.getVillageName() != null && !req.getVillageName().isBlank()) {
            sysUserMapper.updateVillageCode(userId, req.getVillageName());
            login.setVillageCode(req.getVillageName());
        }
        updateNamePhoneIfPresent(req, login, userId);
        return buildProfile(userId);
    }

    /**
     * 姓名 / 手机号变更：写 sys_user，并同步 Redis 会话快照。
     * 手机号是登录凭证，须全库唯一（排除本人）；会话刷新失败不影响资料更新结果。
     */
    private void updateNamePhoneIfPresent(ProfileUpdateRequest req, UserContext.LoginUser login, Long userId) {
        String newName = req.getName() == null ? null : req.getName().trim();
        String newPhone = req.getPhone() == null ? null : req.getPhone().trim();
        if (newName == null && newPhone == null) {
            return;
        }
        if (newName != null && (newName.isEmpty() || newName.length() > 32)) {
            throw BizException.badParam("姓名需 1-32 个字符");
        }
        if (newPhone != null) {
            if (!newPhone.matches("^1[3-9]\\d{9}$")) {
                throw BizException.badParam("手机号格式不正确");
            }
            SysUser owner = sysUserMapper.findByPhone(newPhone);
            if (owner != null && !owner.getId().equals(userId)) {
                throw BizException.badParam("该手机号已被其他账号使用");
            }
        }
        sysUserMapper.updateNamePhone(userId, newName, newPhone);
        if (newName != null) {
            login.setName(newName);
        }
        if (newPhone != null) {
            login.setPhone(newPhone);
        }
        try {
            String key = appProperties.getRedis().getTokenPrefix() + login.getJti();
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            String json = objectMapper.writeValueAsString(login);
            if (ttl != null && ttl > 0) {
                redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // 会话快照刷新失败不影响主流程
        }
    }

    /**
     * 把请求字段拷到实体。
     * insert 模式：直接赋值（含 null）；update 模式：仅非 null 才覆盖（0 会写入）。
     */
    private void applyRequest(VillagerInfo info, ProfileUpdateRequest req, boolean insertMode) {
        if (insertMode || req.getIdNumber() != null) {
            info.setIdNumber(req.getIdNumber());
        }
        if (insertMode || req.getIdFront() != null) {
            info.setIdFront(req.getIdFront());
        }
        if (insertMode || req.getIdBack() != null) {
            info.setIdBack(req.getIdBack());
        }
        if (insertMode || req.getCountyName() != null) {
            info.setCountyName(req.getCountyName());
        }
        if (insertMode || req.getTownName() != null) {
            info.setTownName(req.getTownName());
        }
        if (insertMode || req.getVillageName() != null) {
            info.setVillageName(req.getVillageName());
        }
        if (insertMode || req.getPlantType() != null) {
            info.setPlantType(req.getPlantType());
        }
        // 脱敏卡号勿回写
        if (req.getBankCard() != null && !req.getBankCard().contains("*")) {
            info.setBankCard(req.getBankCard());
        } else if (insertMode) {
            info.setBankCard(null);
        }
        if (insertMode || req.getBankName() != null) {
            info.setBankName(req.getBankName());
        }
        if (insertMode || req.getPlantArea() != null) {
            info.setPlantArea(req.getPlantArea());
        }
        if (insertMode || req.getSubjectType() != null) {
            info.setSubjectType(req.getSubjectType());
        }
        if (insertMode || req.getLaborCount() != null) {
            info.setLaborCount(req.getLaborCount());
        }
        if (insertMode || req.getFarmYears() != null) {
            info.setFarmYears(req.getFarmYears());
        }
        if (insertMode || req.getIsVeteranFamily() != null) {
            info.setIsVeteranFamily(req.getIsVeteranFamily());
        }
        if (insertMode || req.getIsMonitorHousehold() != null) {
            info.setIsMonitorHousehold(req.getIsMonitorHousehold());
        }
        if (insertMode || req.getSecondaryPlantType() != null) {
            info.setSecondaryPlantType(req.getSecondaryPlantType());
        }
        if (insertMode || req.getIrrigatedArea() != null) {
            info.setIrrigatedArea(req.getIrrigatedArea());
        }
        if (insertMode || req.getHasLandCert() != null) {
            info.setHasLandCert(req.getHasLandCert());
        }
        if (insertMode || req.getLivestockType() != null) {
            info.setLivestockType(req.getLivestockType());
        }
        if (insertMode || req.getLivestockCount() != null) {
            info.setLivestockCount(req.getLivestockCount());
        }
        if (insertMode || req.getHasManureFacility() != null) {
            info.setHasManureFacility(req.getHasManureFacility());
        }
        if (insertMode || req.getNeedMachinery() != null) {
            info.setNeedMachinery(req.getNeedMachinery());
        }
        if (insertMode || req.getMachineryType() != null) {
            info.setMachineryType(req.getMachineryType());
        }
        if (insertMode || req.getHasGrainInsurance() != null) {
            info.setHasGrainInsurance(req.getHasGrainInsurance());
        }
    }

    /** 组装前端用的资料 Map（银行卡脱敏，含精细画像字段） */
    private Map<String, Object> buildProfile(Long userId) {
        SysUser user = sysUserMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "用户不存在", 404);
        }
        VillagerInfo info = villagerInfoMapper.findByUserId(userId);
        Map<String, Object> m = new HashMap<>();
        m.put("userId", user.getId());
        m.put("name", user.getName());
        m.put("phone", user.getPhone());
        m.put("idNumber", info == null ? null : info.getIdNumber());
        m.put("idFront", info == null ? null : info.getIdFront());
        m.put("idBack", info == null ? null : info.getIdBack());
        m.put("countyName", info == null ? null : info.getCountyName());
        m.put("townName", info == null ? null : info.getTownName());
        m.put("villageName", info == null ? null : info.getVillageName());
        m.put("plantType", info == null ? null : info.getPlantType());
        m.put("bankCard", info == null ? null : maskBankCard(info.getBankCard()));
        m.put("bankName", info == null ? null : info.getBankName());
        m.put("plantArea", info == null ? null : info.getPlantArea());
        // 精细画像原样返回（布尔用 Integer 0/1）
        m.put("subjectType", info == null ? null : info.getSubjectType());
        m.put("laborCount", info == null ? null : info.getLaborCount());
        m.put("farmYears", info == null ? null : info.getFarmYears());
        m.put("isVeteranFamily", info == null ? null : info.getIsVeteranFamily());
        m.put("isMonitorHousehold", info == null ? null : info.getIsMonitorHousehold());
        m.put("secondaryPlantType", info == null ? null : info.getSecondaryPlantType());
        m.put("irrigatedArea", info == null ? null : info.getIrrigatedArea());
        m.put("hasLandCert", info == null ? null : info.getHasLandCert());
        m.put("livestockType", info == null ? null : info.getLivestockType());
        m.put("livestockCount", info == null ? null : info.getLivestockCount());
        m.put("hasManureFacility", info == null ? null : info.getHasManureFacility());
        m.put("needMachinery", info == null ? null : info.getNeedMachinery());
        m.put("machineryType", info == null ? null : info.getMachineryType());
        m.put("hasGrainInsurance", info == null ? null : info.getHasGrainInsurance());
        return m;
    }

    /** 银行卡脱敏展示：前四后四，中间星号 */
    private String maskBankCard(String card) {
        if (card == null || card.length() < 8) {
            return card;
        }
        return card.substring(0, 4) + "********" + card.substring(card.length() - 4);
    }
}