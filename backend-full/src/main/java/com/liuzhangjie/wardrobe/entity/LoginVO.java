package com.liuzhangjie.wardrobe.entity;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private User user;
}
