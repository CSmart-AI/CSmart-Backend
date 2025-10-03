package Capstone.CSmart.global.apiPayload.exception.handler;


import Capstone.CSmart.global.apiPayload.code.BaseErrorCode;
import Capstone.CSmart.global.apiPayload.exception.GeneralException;

public class MemberHandler extends GeneralException {
    public MemberHandler(BaseErrorCode baseErrorCode){
        super(baseErrorCode);
    }
}
