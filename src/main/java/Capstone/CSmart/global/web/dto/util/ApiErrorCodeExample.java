package Capstone.CSmart.global.web.dto.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import Capstone.CSmart.global.apiPayload.code.status.ErrorStatus;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodeExample {

	ErrorStatus value();
}