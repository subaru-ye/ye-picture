package com.ye.yepicturebackend.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.ye.yepicturebackend.model.dto.space.analyze.*;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.vo.space.analyze.*;

import java.util.List;

public interface SpaceAnalyzeService extends IService<Space> {

    /**
     * 资源使用情况分析
     *
     * @param spaceUsageAnalyzeRequest 空间使用分析请求参数对象
     *                                 包含查询范围标识（isQueryAll：全空间分析；isQueryPublic：公共图库分析）、
     *                                 目标私有空间ID（spaceId）等，用于区分查询场景并确定数据范围
     * @param loginUser                当前登录用户信息对象
     * @return SpaceUsageAnalyzeResponse 空间使用分析结果响应对象
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(
            SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 分类分析
     *
     * @param spaceCategoryAnalyzeRequest 空间分类分析请求参数对象
     *                                    包含查询范围标识（isQueryAll：全空间分析；isQueryPublic：公共图库分析）、
     *                                    目标私有空间ID（spaceId）等，用于区分查询场景并确定数据范围
     * @param loginUser                   当前登录用户信息对象
     * @return List<SpaceCategoryAnalyzeResponse> 空间分类分析结果响应对象列表
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 标签分析
     *
     * @param spaceTagAnalyzeRequest 空间标签分析请求参数对象
     * @param loginUser              当前登录用户信息对象
     * @return SpaceTagAnalyzeResponse 空间标签分析结果响应对象
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 图片大小分析
     *
     * @param spaceSizeAnalyzeRequest 空间大小分析请求参数对象
     * @param loginUser               当前登录用户信息对象
     * @return List<SpaceSizeAnalyzeResponse> 空间大小分析结果响应对象列表
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 用户行为分析
     *
     * @param spaceUserAnalyzeRequest 空间用户分析请求参数对象
     * @param loginUser               当前登录用户信息对象
     * @return List<SpaceUserAnalyzeResponse> 空间用户分析结果响应对象列表
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     * 空间使用排行分析
     *
     * @param spaceRankAnalyzeRequest 空间排行分析请求参数对象
     * @param loginUser               当前登录用户信息对象
     * @return List<Space> 空间排行分析结果响应对象列表
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
