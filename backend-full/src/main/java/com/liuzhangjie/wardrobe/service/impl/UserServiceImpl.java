package com.liuzhangjie.wardrobe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liuzhangjie.wardrobe.common.BusinessException;
import com.liuzhangjie.wardrobe.common.EnableStatus;
import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.common.ResultCode;
import com.liuzhangjie.wardrobe.entity.*;
import com.liuzhangjie.wardrobe.mapper.UserMapper;
import com.liuzhangjie.wardrobe.service.UserService;
import com.liuzhangjie.wardrobe.utils.JwtUtil;
import com.liuzhangjie.wardrobe.utils.UserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil userJwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil userJwtUtil, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.userJwtUtil = userJwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //默认密码
    private static final String DEFAULT_PASSWORD = "123456";


    /**
     * 用户注册
     * @param dto
     */
    @Override
    public void register(RegisterDTO dto) {
        //1判断是否为空
        //dto对象为空 或者 username是空串/全空格
        if (dto == null || !StringUtils.hasText(dto.getUsername())) {
            throw new BusinessException(ResultCode.USERNAMEANDPASSWORD_EMPTY);
        }
        //密码空串/全空格
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(ResultCode.USERNAMEANDPASSWORD_EMPTY);
        }
        if (!dto.getPassword().matches("^.{6,12}$")) {
            throw new BusinessException(ResultCode.PASSWORD_FORMAT_ERROR);
        }
        //2查重
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));

        //3重复抛异常
        if(count>0){
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        //4组装实体
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));//密码加密
       //兜底
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname() : dto.getUsername());

        //插入
        userMapper.insert(user);
    }

    /**
     * 登录
     * @param dto
     * @return
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        //1,判断是否为空
        if(dto.getUsername()==null||dto.getPassword()==null){
            throw new BusinessException(ResultCode.USERNAMEANDPASSWORD_EMPTY);
        }
        //2按照用户名查用户（select id,username,password,nickname,role from user where username = ? limit 1;）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        //3判断用户是否存在,不能直接抛用户名不存在
        /*
        * 如果用户名不存在时明确告诉他"用户不存在"，
        * 攻击者就能拿一个用户名字典批量试探，把
        * "哪些账号真实存在"全摸出来，这叫用户名枚举。
        * 之后他只需要针对这些真实账号去撞密码，
        * 效率高一个数量级。
        * */
        if(user==null){
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        //4校验密码（passwordEncoder.matches(明文密码, 加密后的密文)自带匹配，不需要手动判断）
        if(!passwordEncoder.matches(dto.getPassword(), user.getPassword())){
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        //5帐号状态检查（0停用，1正常）
        if(user.getStatus()==0){
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        //6签发token并组装LoginVO
        String token = userJwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(user);
        //将token存入redis，7200sTTL
        stringRedisTemplate.opsForValue().set(
                "wardrobe:login:token:" + token,
                String.valueOf(user.getId()),
                userJwtUtil.getExpire(),
                TimeUnit.SECONDS);

        return vo;
    }

    /**
     * 根据id回显
     * @param userId
     * @return
     */
    @Override
    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if(user==null){
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }


    /**
     * 修改个人中心数据
     * @return
     */
    @Override
    public void updateById(Long userId, UpdateProfileDTO dto) {
        User user = new User();
        BeanUtils.copyProperties(dto,user);
        user.setId(userId);
        userMapper.updateById(user);
    }

    /**
     * 管理端--分页查询
     * @param page
     * @param size
     * @param keyword
     * @param status
     * @return
     */
    @Override
    public Page<User> page(Integer page, Integer size, String keyword, Integer status) {
        //创建对象
        Page<User> userPage = new Page<>(page,size);

        //创建条件容器
        LambdaQueryWrapper<User>wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(status!=null,User::getStatus,status);
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(User::getUsername, keyword)
                .or()
                .like(User::getNickname, keyword));
        wrapper.orderByDesc(User::getCreateTime);

        //交给mapper
        return userMapper.selectPage(userPage, wrapper);
    }

    /**
     * 启用/禁用账号
     * @param id
     * @param userStatusDTO
     */
    @Override
    public void userStatus(Long id, UserStatusDTO userStatusDTO) {
        //校验status
        Integer status = userStatusDTO.getStatus();
        if (!EnableStatus.ENABLED.getCode().equals(status)
                && !EnableStatus.DISABLED.getCode().equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        //检验是否操作自己
        if(id.equals(UserContext.getUserId())){
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        //检验目标用户是否存在
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        //更新状态
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    /**
     * 重置密码
     * @param id
     */
    @Override
    public void rePassword(Long id) {
        //校验用户存在
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        //加密默认密码
        String defcode = passwordEncoder.encode(DEFAULT_PASSWORD);
        //重置密码
        User user = new User();
        user.setId(id);
        user.setPassword(defcode);
        userMapper.updateById(user);
    }

}
