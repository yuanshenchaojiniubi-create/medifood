package com.medifood.user.util;

import cn.hutool.crypto.digest.BCrypt;

public class BcryptUtil {
    public static String hashpw(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public static boolean checkpw(String password, String hashed) {
        return BCrypt.checkpw(password, hashed);
    }
}