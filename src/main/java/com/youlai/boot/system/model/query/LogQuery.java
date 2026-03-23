package com.youlai.boot.system.model.query;

import com.youlai.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Schema(description = "日志查询对象")
@Getter
@Setter
public class LogQuery extends BaseQuery {

    @Schema(description="关键字(IP/操作人)")
    private String keywords;

    @Schema(description="操作时间范围")
    List<String> createTime;

}
