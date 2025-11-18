package com.ye.yepicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ye.yepicturebackend.common.DeleteRequest;
import com.ye.yepicturebackend.model.vo.SpaceVO;
import com.ye.yepicturebackend.model.dto.space.SpaceAddRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceEditRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceQueryRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceUpdateRequest;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.User;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 空间服务接口
 * <p>
 * 定义空间相关的核心业务操作，包含通用增删改查、管理员专属操作、VO转换、数据校验等功能，
 * 所有业务逻辑实现需遵循接口定义的入参、返回值规范。
 */
public interface SpaceService extends IService<Space> {

    // region 通用增删改查

    /**
     * 创建用户私人空间
     *
     * @param spaceAddRequest 空间创建请求参数对象，核心字段：
     *                        - spaceName：空间名称（未传时默认"默认空间"）
     *                        - spaceLevel：空间级别（未传时默认普通版）
     * @param loginUser       当前登录用户实体对象，用于：
     *                        - 绑定空间所属用户ID（userId）
     *                        - 校验管理员专属级别的创建权限
     * @return long 成功创建的空间ID；若创建失败（如事务异常、业务规则限制），返回-1L
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 删除指定空间
     *
     * @param deleteRequest 包含待删除空间ID的请求体，通过getId()获取空间ID（需为正整数）
     * @param loginUser     当前登录用户对象，用于权限校验：
     *                      - 空间所有者可删除自己的空间
     *                      - 管理员可删除所有空间
     * @return Map<String, Object> 删除结果映射：
     * - dbDeleted：Boolean类型，表示数据库删除操作是否成功
     * - 可扩展字段：如fileDeleted（文件删除状态）、deleteTime（删除时间）等
     */
    Map<String, Object> deleteSpace(DeleteRequest deleteRequest, User loginUser);

    /**
     * 编辑空间基础信息
     *
     * @param spaceEditRequest 空间编辑请求体，核心字段：
     *                         - id：待编辑的空间ID（必传，需为正整数）
     *                         - spaceName：空间名称（可选，长度≤30）
     *                         - description：空间简介（可选）
     * @param loginUser        当前登录用户对象，用于权限校验：
     *                         - 空间所有者可编辑自己的空间
     *                         - 管理员可编辑所有空间
     * @return Map<String, Object> 编辑结果详情：
     * - spaceId：被编辑的空间ID
     * - edited：Boolean类型，表示编辑操作是否成功
     * - editTime：实际编辑时间（Date类型）
     * - message：操作提示信息（如"空间编辑成功"）
     */
    Map<String, Object> editSpace(SpaceEditRequest spaceEditRequest, User loginUser);

    // endregion

    // region 管理员相关
    // 注：该模块包含仅管理员可执行的操作，需在实现中确保权限注解或逻辑校验

    /**
     * 管理员更新空间（支持级别变更）
     *
     * @param spaceUpdateRequest 空间更新请求体，核心字段：
     *                           - id：待更新的空间ID（必传，需为正整数）
     *                           - spaceName：空间名称（可选，长度≤30）
     *                           - spaceLevel：空间级别（可选，仅支持0=普通版、1=专业版、2=旗舰版）
     *                           - description：空间描述（可选）
     * @return Map<String, Object> 详细的更新结果：
     * - spaceId：被更新的空间ID（Long）
     * - updated：Boolean类型，恒为true（失败会直接抛异常）
     * - oldSpaceLevel：更新前的空间级别值（Integer，如0/1/2）
     * - oldSpaceLevelText：更新前的空间级别文本（String，如"普通版"）
     * - newSpaceLevel：更新后的空间级别值（Integer，无变更则为null）
     * - newSpaceLevelText：更新后的空间级别文本（String，无变更则为null）
     * - updateTime：更新操作时间（Date）
     * - message：操作提示信息（如"空间级别从普通版变更为专业版"）
     */
    Map<String, Object> updateSpace(SpaceUpdateRequest spaceUpdateRequest);

    // endregion

    // region 数据填充与校验

    /**
     * 根据空间级别自动填充容量配置
     *
     * @param space 待填充的空间实体对象，需包含spaceLevel字段（空间级别），
     *              填充字段：
     *              - maxCount：该级别允许的最大图片数量（从SpaceLevelEnum获取）
     *              - maxSize：该级别允许的最大空间容量（从SpaceLevelEnum获取）
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 校验空间实体数据合法性
     *
     * @param space 需要校验的空间实体对象
     * @param add   操作类型标识：
     *              - true：创建操作，需校验必填字段（如spaceName、spaceLevel）
     *              - false：更新操作，仅校验非空字段的合法性（如spaceName长度≤30）
     */
    void validSpace(Space space, boolean add);

    /**
     * 校验用户对指定空间的操作权限
     *
     * @param loginUser 当前登录用户信息对象
     *                  需包含用户唯一标识（id），用于判断是否为空间所有者或管理员
     * @param space     待操作的空间对象
     *                  需包含空间所属用户标识（userId），用于与登录用户id匹配
     */
    void checkSpaceAuth(User loginUser,Space space);
    // endregion

    // region 查询与转换

    /**
     * 构建空间查询条件
     *
     * @param spaceQueryRequest 空间查询请求参数对象，支持字段：
     *                          - id：空间ID（精准匹配）
     *                          - userId：用户ID（精准匹配，查询指定用户的空间）
     *                          - spaceName：空间名称（模糊匹配）
     *                          - spaceLevel：空间级别（精准匹配）
     *                          - sortField：排序字段（支持createTime、spaceName等）
     *                          - sortOrder：排序方向（ascend=升序，其他=降序）
     * @return LambdaQueryWrapper<Space> 构建完成的MyBatis-Plus查询构造器，可直接用于分页/列表查询
     */
    LambdaQueryWrapper<Space> getLambdaQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 空间实体转换为VO（视图对象）
     *
     * @param space   数据库查询得到的空间实体对象
     * @param request HTTP请求对象，用于关联查询用户信息（适配后续扩展需求）
     * @return SpaceVO 前端展示用的空间视图对象，包含用户信息（UserVO）、级别文本等扩展字段
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 空间实体分页转换为VO分页
     *
     * @param spacePage 数据库查询得到的空间实体分页对象（包含总条数、当前页数据）
     * @param request   HTTP请求对象，用于批量关联查询用户信息（提升性能）
     * @return Page<SpaceVO> 前端可直接渲染的空间VO分页对象，包含分页参数和VO列表
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    // endregion

}