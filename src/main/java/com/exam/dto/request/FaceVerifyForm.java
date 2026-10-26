package com.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开考前的人脸核验请求。
 */
@Data
public class FaceVerifyForm {

    @NotNull(message = "考试ID不能为空")
    private Long examId;

    /** 现场抓拍的 JPEG（base64） */
    @NotBlank(message = "请先完成人脸采集")
    private String imageBase64;

    /**
     * 活体是否通过（前端眨眼检测结果）。
     *
     * <p>不由前端决定「能不能过」：活体只是必要条件，身份比对仍然在服务端
     * 调厂商接口得出。前端传 false 时服务端连接口都不会调（省一次调用费）。</p>
     */
    private Boolean liveness;
}