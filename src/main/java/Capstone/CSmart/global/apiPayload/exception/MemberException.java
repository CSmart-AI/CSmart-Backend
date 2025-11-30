package Capstone.CSmart.global.apiPayload.exception;

import Capstone.CSmart.global.apiPayload.code.BaseErrorCode;

public class MemberException extends GeneralException {
    public MemberException(BaseErrorCode code) {
        super(code);
    }
}

