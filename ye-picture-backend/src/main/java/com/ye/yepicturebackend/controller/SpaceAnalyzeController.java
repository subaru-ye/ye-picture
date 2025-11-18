package com.ye.yepicturebackend.controller;


import com.ye.yepicturebackend.common.BaseResponse;
import com.ye.yepicturebackend.common.ResultUtils;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import com.ye.yepicturebackend.model.dto.space.analyze.*;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.vo.space.analyze.*;
import com.ye.yepicturebackend.service.SpaceAnalyzeService;
import com.ye.yepicturebackend.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space/Analyze")
public class SpaceAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;

    /**
     * 获取空间资源使用情况分析
     *
     * @param spaceUsageAnalyzeRequest 空间使用分析请求参数对象
     * @param request                  当前请求对象，用于获取登录用户信息
     * @return 空间使用分析结果响应对象
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyze(
            @RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,
            HttpServletRequest request) {
        // 1. 校验并获取参数
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null,
                ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        User loginUser = userService.getLoginUser(request);

        // 2. 调用服务层方法获取空间使用情况分析结果
        SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = spaceAnalyzeService.getSpaceUsageAnalyze(spaceUsageAnalyzeRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(spaceUsageAnalyzeResponse);
    }

    /**
     * 获取空间资源分类使用情况分析
     *
     * @param spaceCategoryAnalyzeRequest 空间分类使用分析请求参数对象
     * @param request                     当前请求对象，用于获取登录用户信息
     * @return 空间分类使用分析结果响应对象
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(
            @RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest,
            HttpServletRequest request) {
        // 1. 校验并获取参数
        User loginUser = userService.getLoginUser(request);

        // 2. 调用服务层方法获取空间使用情况分析结果
        List<SpaceCategoryAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceCategoryAnalyze(spaceCategoryAnalyzeRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(resultList);
    }

    /**
     * 获取空间资源标签使用情况分析
     *
     * @param spaceTagAnalyzeRequest 空间标签使用分析请求参数对象
     * @param request                当前请求对象，用于获取登录用户信息
     * @return 空间标签使用分析结果响应对象
     */
    @PostMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> getSpaceTagAnalyze(
            @RequestBody SpaceTagAnalyzeRequest spaceTagAnalyzeRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);

        // 2. 调用服务层方法获取空间标签使用情况分析结果
        List<SpaceTagAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceTagAnalyze(spaceTagAnalyzeRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(resultList);
    }

    /**
     * 获取空间图片大小分析
     *
     * @param spaceSizeAnalyzeRequest 空间图片大小分析请求参数对象
     * @param request                 当前请求对象，用于获取登录用户信息
     * @return 空间图片大小分析结果响应对象
     */
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyze(
            @RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);

        // 2. 调用服务层方法获取空间图片大小分析结果
        List<SpaceSizeAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceSizeAnalyze(spaceSizeAnalyzeRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(resultList);
    }

    /**
     * 获取空间用户行为分析
     *
     * @param spaceUserAnalyzeRequest 空间用户行为分析请求参数对象
     * @param request                 当前请求对象，用于获取登录用户信息
     * @return 空间用户行为分析结果响应对象
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyze(
            @RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);

        // 2. 调用服务层方法获取空间用户行为分析结果
        List<SpaceUserAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceUserAnalyze(spaceUserAnalyzeRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(resultList);
    }

    /**
     * 获取空间资源排行榜
     *
     * @param spaceRankAnalyzeRequest 空间资源排行榜请求参数对象
     * @param request                 当前请求对象，用于获取登录用户信息
     * @return 空间资源排行榜结果响应对象
     */
    @PostMapping("/rank")
    public BaseResponse<List<Space>> getSpaceRankAnalyze(
            @RequestBody SpaceRankAnalyzeRequest spaceRankAnalyzeRequest,
            HttpServletRequest request) {
        // 1. 获取参数
        User loginUser = userService.getLoginUser(request);

        // 2. 调用服务层方法获取空间资源排行榜结果
        List<Space> resultList = spaceAnalyzeService.getSpaceRankAnalyze(spaceRankAnalyzeRequest, loginUser);

        // 3. 返回结果
        return ResultUtils.success(resultList);
    }

}


















