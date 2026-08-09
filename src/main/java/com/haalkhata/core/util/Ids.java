package com.haalkhata.core.util;

import java.util.UUID;

public final class Ids {

    private Ids() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String shopId(String userId) {
        return "shop_" + userId;
    }

    public static String randomCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) ('0' + (int) (Math.random() * 10)));
        }
        return sb.toString();
    }
}
