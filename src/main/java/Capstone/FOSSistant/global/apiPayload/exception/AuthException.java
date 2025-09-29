package Capstone.FOSSistant.global.apiPayload.exception;


import Capstone.FOSSistant.global.apiPayload.code.BaseErrorCode;

public class AuthException extends GeneralException {

    public AuthException(BaseErrorCode code) {
        super(code);
    }
}