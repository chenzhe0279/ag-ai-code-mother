package com.ag.agaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.ag.agaicodemother.constant.FileConstant;
import com.ag.agaicodemother.exception.BusinessException;
import com.ag.agaicodemother.exception.ErrorCode;
import com.ag.agaicodemother.exception.ThrowUtils;
import com.ag.agaicodemother.model.dto.user.UserQueryRequest;
import com.ag.agaicodemother.model.enums.UserRoleEnum;
import com.ag.agaicodemother.model.vo.LoginUserVO;
import com.ag.agaicodemother.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ag.agaicodemother.model.entity.User;
import com.ag.agaicodemother.mapper.UserMapper;
import com.ag.agaicodemother.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ag.agaicodemother.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author <a href="https://github.com/chenzhe0279">陈爱国</a>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

    private static final Set<String> ALLOWED_AVATAR_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024L;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查是否重复
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "ag";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }


    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public String uploadAvatar(MultipartFile file, HttpServletRequest request) {
        // 从当前会话中获取登录用户信息
        User user = getLoginUser(request);
        // 校验上传的文件是否为空
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择图片文件");
        // 校验文件大小是否超过上限（5MB）
        ThrowUtils.throwIf(file.getSize() > MAX_AVATAR_SIZE,
                ErrorCode.PARAMS_ERROR, "图片大小不能超过 5MB");

        // 获取原始文件名，若为空则使用默认名称 avatar.jpg
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "avatar.jpg");
        // 提取文件扩展名并转为小写
        String ext = StrUtil.subAfter(originalName, ".", true).toLowerCase();
        // 校验扩展名是否在允许的图片格式列表中
        ThrowUtils.throwIf(!ALLOWED_AVATAR_EXTS.contains(ext),
                ErrorCode.PARAMS_ERROR, "仅支持 jpg / jpeg / png / gif / webp 格式");

        // 构造头像存储目录：基础保存路径 + /avatar
        String dir = FileConstant.FILE_SAVE_DIR + "/avatar";
        // 确保目录存在（不存在则创建）
        FileUtil.mkdir(dir);
        // 生成唯一文件名：UUID + 扩展名
        String fileName = IdUtil.fastSimpleUUID() + "." + ext;
        // 构造目标文件对象
        File dest = new File(dir, fileName);
        try {
            // 将上传的文件保存到目标位置
            file.transferTo(dest);
        } catch (IOException e) {
            // 记录保存失败日志并抛出系统错误异常
            log.error("头像保存失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像保存失败");
        }

        // 构造头像访问 URL
        String avatarUrl = request.getContextPath() + "/file/avatar/" + fileName;
        // 创建待更新的用户对象，只设置 id 和新头像地址
        User upLoadUser = new User();
        upLoadUser.setId(user.getId());
        upLoadUser.setUserAvatar(avatarUrl);
        // 更新数据库中用户的头像信息
        boolean updateResult = this.updateById(upLoadUser);
        if (!updateResult) {
            // 更新失败则记录错误日志并抛出异常
            log.error("用户 {} 更新头像失败：{}", user.getId(), avatarUrl);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新头像失败");
        }
        // 记录上传成功日志
        log.info("用户 {} 上传新头像：{}", user.getId(), avatarUrl);
        // 返回头像访问 URL
        return avatarUrl;
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        // 用户不存在
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 4. 获得脱敏后的用户信息
        return this.getLoginUserVO(user);
    }


    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接返回上述结果）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

}
