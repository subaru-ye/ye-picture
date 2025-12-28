package com.ye.yepicturebackend.model.dto.picture.task;

import com.ye.yepicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serializable;

/**
 * AI扩图请求类
 */
@Data
public class AiExtendRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 扩图参数
     */
    private CreateOutPaintingTaskRequest.Parameters parameters;

    private static final long serialVersionUID = 957269315675927017L;
}
