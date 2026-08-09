package com.haalkhata.feature.auth.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MailService {

    public void sendVerificationCode(String email, String code) {
        log.info("EMAIL_OUT to={} type=email-verification code={} (console provider)", email, code);
    }

    public void sendPasswordResetCode(String email, String code) {
        log.info("EMAIL_OUT to={} type=password-reset code={} (console provider)", email, code);
    }

    public void sendOtp(String phone, String code) {
        log.info("SMS_OUT to={} type=otp code={} (console provider)", phone, code);
    }
}
