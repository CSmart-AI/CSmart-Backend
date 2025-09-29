#!/bin/bash

BASE_DIR=src/main/java/Capstone/FOSSistant/global

mkdir -p $BASE_DIR/apiPayload/code/status
mkdir -p $BASE_DIR/apiPayload/exception/handler
mkdir -p $BASE_DIR/config
mkdir -p $BASE_DIR/converter
mkdir -p $BASE_DIR/domain/{common,entity,enums}
mkdir -p $BASE_DIR/repository
mkdir -p $BASE_DIR/security/{filter,handler/annotation,handler/resolver,principal,provider}
mkdir -p $BASE_DIR/service
mkdir -p $BASE_DIR/web/{controller,dto}

touch $BASE_DIR/apiPayload/code/status/ErrorStatus.java
touch $BASE_DIR/apiPayload/code/status/SuccessStatus.java
touch $BASE_DIR/apiPayload/code/BaseCode.java
touch $BASE_DIR/apiPayload/code/BaseErrorCode.java
touch $BASE_DIR/apiPayload/ApiResponse.java

touch $BASE_DIR/config/SecurityConfig.java
touch $BASE_DIR/config/SwaggerConfig.java
touch $BASE_DIR/config/WebConfig.java