package Capstone.CSmart.global.apiPayload.exception;


import Capstone.CSmart.global.apiPayload.code.BaseErrorCode;

public class AuthException extends GeneralException {

    public AuthException(BaseErrorCode code) {
        super(code);
    }
}