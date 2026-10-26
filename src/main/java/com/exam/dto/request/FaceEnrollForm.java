package com.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 人脸底片录入（注册时 / 登录后自助补录）。
 */
@Data
public class FaceEnrollForm {

    /** 前端抓拍并压缩后的 JPEG（data:image/jpeg;base64,... 或纯 base64） */
    @NotBlank(message = "请先完成人脸采集")
    private String imageBase64;

    /** 活体是否通过（前端眨眼检测的结果）。服务端记录，不通过则拒绝录入 */
    private Boolean liveness;

    /** 录入来源：注册页传 ENROLL，自助补录页可不传（默认 SELF） */
    private String source;
}