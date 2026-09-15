package com.ag.agaicodemother.model.dto.user;

import lombok.Data;
import java.io.Serializable;

/**
 * 当前登录用户修改自己的资料
 * 只暴露昵称、头像、简介，账号与角色不允许用户自己修改
 */
@Data
public class UserUpdateMyRequest implements Serializable {
    private String userName;
    private String userAvatar;
    private String userProfile;
    private static final long serialVersionUID = 1L;
}