package com.trip.module.user.service;

import com.trip.module.user.dto.LoginDTO;
import com.trip.module.user.dto.RegisterDTO;
import com.trip.module.user.vo.LoginVO;
import com.trip.module.user.vo.UserVO;

public interface UserService {

    LoginVO login(LoginDTO dto);

    UserVO register(RegisterDTO dto);

    UserVO getCurrentUser(Long userId);
}