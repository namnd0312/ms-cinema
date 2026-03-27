package com.namnd.cinema.dto.mapper;


import com.namnd.cinema.dto.RegisterDto;
import com.namnd.cinema.model.User;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
public class RegisterDtoMapper {

    public User toEntity(RegisterDto dto) {
        if (dto == null) {
            return null;
        }

        User user = new User();
        BeanUtils.copyProperties(dto, user);
        // Password left null - set during activation
        return user;
    }
}
