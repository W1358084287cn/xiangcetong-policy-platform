package com.xiangcetong.user.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.Role;
import com.xiangcetong.log.service.OperationLogService;
import com.xiangcetong.user.dto.CreateUserRequest;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.SysUserMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统管理员：用户列表与创建。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Set<String> ROLES = Role.ALL;

    private final SysUserMapper sysUserMapper;
    private final VillagerInfoMapper villagerInfoMapper;
    private final OperationLogService operationLogService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(SysUserMapper sysUserMapper,
                                VillagerInfoMapper villagerInfoMapper,
                                OperationLogService operationLogService,
                                PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.villagerInfoMapper = villagerInfoMapper;
        this.operationLogService = operationLogService;
        this.passwordEncoder = ***;
    }

    /** 分页用户列表（不含密码） */
    @Override
    public PageResult<Map<String, Object>> list(String role, String phone,
                                                Integer pageNum, Integer pageSize) {
        UserContext.requireRole(Role.SYS_ADMIN);
        int pn = PageResult.normalizePageNum(pageNum);
        int ps = PageResult.normalizePageSize(pageSize);
        long total = sysUserMapper.countAdmin(role, phone);
        List<SysUser> rows = sysUserMapper.listAdmin(role, phone, PageResult.offset(pn, ps), ps);
        List<Map<String, Object>> list = rows.stream().map(this::toVo).collect(Collectors.toList());
        return new PageResult<>(list, total, pn, ps);
    }

    /** 创建用户；村民角色顺带建空 villager_info */
    @Transactional
    @Override
    public Map<String, Object> create(CreateUserRequest req) {
        UserContext.LoginUser admin = UserContext.requireRole(Role.SYS_ADMIN);
        if (!ROLES.contains(req.getRole())) {
            throw BizException.badParam("非法角色");
        }
        // 村/镇必填校验
        if ((Role.VILLAGER.equals(req.getRole()) || Role.VILLAGE_ADMIN.equals(req.getRole()))
                && (req.getVillageCode() == null || req.getVillageCode().isBlank())) {
            throw BizException.badParam("该角色必须填写 villageCode");
        }
        if ((Role.VILLAGE_ADMIN.equals(req.getRole()) || Role.TOWN_ADMIN.equals(req.getRole()))
                && (req.getTownCode() == null || req.getTownCode().isBlank())) {
            throw BizException.badParam("该角色必须填写 townCode");
        }
        if (sysUserMapper.findByPhone(req.getPhone()) != null) {
            throw new BizException(ErrorCodes.CONFLICT, "手机号已注册");
        }

        SysUser user = new SysUser();
        user.setPhone(req.getPhone());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setName(req.getName());
        user.setRole(req.getRole());
        user.setCountyCode(req.getCountyCode());
        user.setTownCode(req.getTownCode());
        user.setVillageCode(req.getVillageCode());
        sysUserMapper.insert(user);

        if (Role.VILLAGER.equals(req.getRole())) {
            VillagerInfo info = new VillagerInfo();
            info.setUserId(user.getId());
            info.setCountyName(req.getCountyCode());
            info.setTownName(req.getTownCode());
            info.setVillageName(req.getVillageCode());
            villagerInfoMapper.insert(info);
        }

        operationLogService.record(admin.getUserId(), "USER_CREATE",
                "sys_user:" + user.getId(), "创建用户 " + user.getPhone());
        return toVo(sysUserMapper.findById(user.getId()));
    }

    /** 删除用户（同时删 villager_info） */
    @Transactional
    @Override
    public void delete(Long id) {
        UserContext.LoginUser admin = UserContext.requireRole(Role.SYS_ADMIN);

        SysUser existing = sysUserMapper.findById(id);
        if (existing == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        if (admin.getUserId().equals(id)) {
            throw BizException.badParam("不能删除自己");
        }

        sysUserMapper.deleteById(id);

        operationLogService.record(admin.getUserId(), "USER_DELETE",
                "sys_user:" + id, "删除用户 " + existing.getPhone());
    }

    private Map<String, Object> toVo(SysUser u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("phone", u.getPhone());
        m.put("name", u.getName());
        m.put("role", u.getRole());
        m.put("countyCode", u.getCountyCode());
        m.put("townCode", u.getTownCode());
        m.put("villageCode", u.getVillageCode());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }
}