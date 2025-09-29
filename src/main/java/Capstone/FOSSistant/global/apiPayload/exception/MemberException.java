package Capstone.FOSSistant.global.apiPayload.exception;

import Capstone.FOSSistant.global.apiPayload.code.BaseErrorCode;

public class MemberException extends GeneralException {
    public MemberException(BaseErrorCode code) {
        super(code);
    }
}

