package Capstone.FOSSistant.global.apiPayload.exception.handler;


import Capstone.FOSSistant.global.apiPayload.code.BaseErrorCode;
import Capstone.FOSSistant.global.apiPayload.exception.GeneralException;

public class MemberHandler extends GeneralException {
    public MemberHandler(BaseErrorCode baseErrorCode){
        super(baseErrorCode);
    }
}
