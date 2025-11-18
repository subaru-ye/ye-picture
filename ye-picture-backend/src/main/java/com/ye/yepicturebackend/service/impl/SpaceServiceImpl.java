package com.ye.yepicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ye.yepicturebackend.common.DeleteRequest;
import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import com.ye.yepicturebackend.exception.ThrowUtils;
import com.ye.yepicturebackend.model.entity.SpaceUser;
import com.ye.yepicturebackend.model.enums.SpaceRoleEnum;
import com.ye.yepicturebackend.model.enums.SpaceTypeEnum;
import com.ye.yepicturebackend.model.vo.SpaceVO;
import com.ye.yepicturebackend.model.vo.UserVO;
import com.ye.yepicturebackend.model.dto.space.SpaceAddRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceEditRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceQueryRequest;
import com.ye.yepicturebackend.model.dto.space.SpaceUpdateRequest;
import com.ye.yepicturebackend.model.entity.Picture;
import com.ye.yepicturebackend.model.entity.Space;
import com.ye.yepicturebackend.model.entity.User;
import com.ye.yepicturebackend.model.enums.SpaceLevelEnum;
import com.ye.yepicturebackend.service.PictureService;
import com.ye.yepicturebackend.service.SpaceService;
import com.ye.yepicturebackend.mapper.SpaceMapper;
import com.ye.yepicturebackend.service.SpaceUserService;
import com.ye.yepicturebackend.service.UserService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Lazy
    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceUserService spaceUserService;

    // region 通用增删改查

    /**
     * 创建用户私人空间
     *
     * @param spaceAddRequest 空间创建请求参数对象，包含：
     *                        - spaceName：空间名称
     *                        - spaceLevel：空间级别
     * @param loginUser       当前登录用户实体对象，用于：
     *                        - 绑定空间所属用户ID（userId）
     *                        - 校验管理员权限
     * @return long 成功创建的空间ID；若创建失败（如事务异常），返回-1L
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceAddRequest == null,
                ErrorCode.PARAMS_ERROR, "请求为空");

        // 2. 构建空间实体
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 2.1 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (spaceAddRequest.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 2.2 填充容量和大小
        this.fillSpaceBySpaceLevel(space);

        // 3. 校验空间实体
        this.validSpace(space, true);

        // 4. 权限与业务规则校验及空间创建（核心业务逻辑）
        // 4.1 获取当前登录用户ID
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 4.2 校验空间级别权限
        ThrowUtils.throwIf(
                SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel()
                        && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        // 4.3 针对用户ID创建线程锁
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 4.3 每个用户仅能创建一个私人空间或一个公共空间
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType, spaceAddRequest.getSpaceType())
                        .exists();
                ThrowUtils.throwIf(exists,
                        ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");
                // 4.4 执行数据库插入
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result,
                        ErrorCode.OPERATION_ERROR, "添加空间失败");
                // 如果是团队空间，关联新增团队成员记录
                if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 4.5 事务成功：返回新创建的空间ID
                return space.getId();
            });
            // 4.5 处理事务执行结果：返回空间ID，若为null则返回-1
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    /**
     * 删除指定空间
     *
     * @param deleteRequest 包含待删除空间ID的请求体，通过getId()获取空间ID
     * @param loginUser     当前登录用户对象，用于权限校验（判断是否为空间所有者或管理员）
     * @return Map<String, Object> 删除结果映射：
     * - 键："dbDeleted"，值：Boolean类型，表示数据库删除操作是否成功
     * - 可扩展其他键值对（如文件删除状态等）
     */
    @Override
    public Map<String, Object> deleteSpace(DeleteRequest deleteRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "空间ID不合法");

        // 2. 查询空间信息
        Long spaceId = deleteRequest.getId();
        Space oldSpace = this.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null,
                ErrorCode.NOT_FOUND_ERROR, "空间不存在或已被删除");

        // 3. 权限校验
        checkSpaceAuth(loginUser, oldSpace);

        // 用于接收事务执行结果
        Map<String, Object> resultMap = new HashMap<>(3);
        // 开启事务，确保空间删除和照片删除要么都成功，要么都回滚
        transactionTemplate.execute(status -> {
            // 4. 查询该空间下的所有照片
            LambdaQueryWrapper<Picture> pictureQueryWrapper = new LambdaQueryWrapper<>();
            pictureQueryWrapper.eq(Picture::getSpaceId, spaceId);
            List<Picture> pictureList = pictureService.list(pictureQueryWrapper);

            // 5. 遍历删除每张照片
            List<Map<String, Object>> pictureDeleteResults = new ArrayList<>();
            for (Picture picture : pictureList) {
                DeleteRequest pictureDeleteRequest = new DeleteRequest();
                pictureDeleteRequest.setId(picture.getId());
                Map<String, Object> deleteResult = pictureService.deletePicture(pictureDeleteRequest, loginUser);
                pictureDeleteResults.add(deleteResult);
            }

            // 6. 执行数据库删除空间
            boolean dbDeleted = this.removeById(spaceId);
            ThrowUtils.throwIf(!dbDeleted,
                    ErrorCode.OPERATION_ERROR, "数据库删除空间失败");

            // 7. 构建返回结果
            resultMap.put("dbDeleted", true);
            resultMap.put("pictureDeleteResults", pictureDeleteResults);
            resultMap.put("message", "空间及其中照片删除成功");
            return null;
        });

        return resultMap;
    }

    /**
     * 编辑空间
     *
     * @param spaceEditRequest 空间编辑请求体，包含待编辑的空间ID、标签、简介等字段
     * @param loginUser        当前登录用户，用于权限校验
     * @return Map<String, Object> 编辑结果详情：
     * - spaceId: 被编辑的空间ID
     * - edited: 布尔值，表示是否编辑成功
     * - editTime: 实际编辑时间
     * - message: 操作提示信息
     */
    @Override
    public Map<String, Object> editSpace(SpaceEditRequest spaceEditRequest, User loginUser) {
        // 1. 提取与校验参数
        ThrowUtils.throwIf(spaceEditRequest == null || spaceEditRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "空间ID不合法（需为正整数）");
        Long spaceId = spaceEditRequest.getId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0,
                ErrorCode.PARAMS_ERROR, "空间ID不合法（需为正整数）");

        // 2. 校验原空间信息
        Space oldSpace = this.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null,
                ErrorCode.NOT_FOUND_ERROR, "待编辑的空间不存在或已被删除");

        // 3. 权限校验：仅空间所有者或管理员可编辑
        checkSpaceAuth(loginUser, oldSpace);

        // 4. 构建编辑对象
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 设置编辑时间
        Date editTime = new Date();
        space.setEditTime(editTime);

        // 5. 空间数据校验
        this.validSpace(space, true);

        // 6. 执行数据库更新
        boolean result = this.updateById(space);
        ThrowUtils.throwIf(!result,
                ErrorCode.OPERATION_ERROR, "空间编辑失败");

        // 7. 构建详细返回结果
        Map<String, Object> resultMap = new HashMap<>(4);
        resultMap.put("spaceId", spaceId);
        resultMap.put("edited", true);
        resultMap.put("editTime", editTime);
        resultMap.put("message", "空间编辑成功");

        return resultMap;
    }

    /**
     * 空间查询请求参数转换
     *
     * @param spaceQueryRequest 空间查询请求参数对象
     * @return LambdaQueryWrapper<Space> 构建完成的查询条件封装器，可直接用于MyBatis-Plus的查询方法
     */
    @Override
    public LambdaQueryWrapper<Space> getLambdaQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        // 1. 初始化查询构造器
        LambdaQueryWrapper<Space> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (spaceQueryRequest == null) {
            log.warn("空间查询请求参数为空，返回默认查询构造器（无任何查询条件）");
            return lambdaQueryWrapper;
        }

        // 2. 提取请求参数
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        // 排序相关参数
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        // 3. 构建查询条件
        // 3.1 精准匹配：id、userId、spaceLevel
        lambdaQueryWrapper
                .eq(ObjUtil.isNotEmpty(id), Space::getId, id)
                .eq(ObjUtil.isNotEmpty(userId), Space::getUserId, userId)
                .eq(ObjUtil.isNotEmpty(spaceLevel), Space::getSpaceLevel, spaceLevel)
                .eq(ObjUtil.isNotEmpty(spaceType), Space::getSpaceType, spaceType);

        // 3.2 模糊匹配：空间名称
        if (StrUtil.isNotBlank(spaceName)) {
            log.info("执行空间名称模糊搜索，搜索关键词：{}", spaceName);
            lambdaQueryWrapper.like(Space::getSpaceName, spaceName);
        }

        // 4. 构建排序条件
        // 4.1 定义合法排序字段映射表：key=前端传的排序字段名，value=Space实体的字段getter方法
        Map<String, SFunction<Space, ?>> sortFieldMap = new HashMap<>(4);
        sortFieldMap.put("createTime", Space::getCreateTime);
        sortFieldMap.put("spaceName", Space::getSpaceName);
        sortFieldMap.put("spaceLevel", Space::getSpaceLevel);
        sortFieldMap.put("updateTime", Space::getUpdateTime);

        // 4.2 校验并应用排序条件
        if (StrUtil.isNotEmpty(sortField) && sortFieldMap.containsKey(sortField)) {
            boolean isAsc = "ascend".equals(sortOrder);
            log.debug("空间查询排序规则：字段={}，方向={}", sortField, isAsc ? "升序" : "降序");
            lambdaQueryWrapper.orderBy(true, isAsc, sortFieldMap.get(sortField));
        } else {
            log.debug("空间查询未指定合法排序字段，使用默认规则：按创建时间降序");
            lambdaQueryWrapper.orderByDesc(Space::getCreateTime);
        }

        // 5. 返回构建完成的查询构造器
        return lambdaQueryWrapper;
    }

    // endregion

    // region 管理员相关

    /**
     * 管理员更新空间
     *
     * @param spaceUpdateRequest 空间更新请求体
     *                           - 必须包含：id（待更新的空间ID）
     *                           - 可选包含：spaceName（空间名称）、description（描述）、
     *                           spaceLevel（空间级别：0=普通版、1=专业版、2=旗舰版）等字段
     * @return Map<String, Object> 详细的更新结果，包含以下信息：
     * - spaceId：被更新的空间ID（Long）
     * - updated：更新是否成功（Boolean，始终为true，失败会直接抛异常）
     * - oldSpaceLevel：更新前的空间级别值（Integer，如0/1/2）
     * - oldSpaceLevelText：更新前的空间级别文本（String，如"普通版"）
     * - newSpaceLevel：更新后的空间级别值（Integer，无变更则为null）
     * - newSpaceLevelText：更新后的空间级别文本（String，无变更则为null）
     * - updateTime：更新操作的时间（Date）
     * - message：操作结果提示信息（String）
     */
    @Override
    public Map<String, Object> updateSpace(SpaceUpdateRequest spaceUpdateRequest) {
        // 1. 校验参数
        Long spaceId = spaceUpdateRequest.getId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0,
                ErrorCode.PARAMS_ERROR, "空间ID不合法（需为正整数）");

        // 2. 查询原始空间信息
        Space oldSpace = this.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null,
                ErrorCode.NOT_FOUND_ERROR, "待更新的空间不存在或已被删除");

        // 3. 获取并记录更新前的空间级别信息
        Integer oldLevelValue = oldSpace.getSpaceLevel();
        SpaceLevelEnum oldLevelEnum = SpaceLevelEnum.getEnumByValue(oldLevelValue);
        String oldLevelText = (oldLevelEnum != null) ? oldLevelEnum.getText() : "未知级别";

        // 4. 处理新的空间级别
        Integer newLevelValue = spaceUpdateRequest.getSpaceLevel();
        SpaceLevelEnum newLevelEnum = null;
        String newLevelText = null;
        if (newLevelValue != null) {
            // 校验新级别是否合法
            newLevelEnum = SpaceLevelEnum.getEnumByValue(newLevelValue);
            ThrowUtils.throwIf(newLevelEnum == null,
                    ErrorCode.PARAMS_ERROR, "空间级别不合法（仅支持：0=普通版、1=专业版、2=旗舰版）");
            // 获取新级别的文本描述
            newLevelText = newLevelEnum.getText();
        }

        // 5. 构建更新对象
        Space updateSpace = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, updateSpace);
        // 根据级别设置容量配置
        if (newLevelEnum != null) {
            updateSpace.setMaxCount(newLevelEnum.getMaxCount());  // 从枚举获取最大文件数
            updateSpace.setMaxSize(newLevelEnum.getMaxSize());    // 从枚举获取最大空间容量
        } else {
            updateSpace.setMaxCount(oldSpace.getMaxCount());  // 沿用旧配置
            updateSpace.setMaxSize(oldSpace.getMaxSize());
        }
        // 设置更新时间
        Date updateTime = new Date();
        updateSpace.setUpdateTime(updateTime);

        // 6. 校验更新对象的数据合法性
        this.validSpace(updateSpace, false);

        // 7. 执行数据库
        boolean updateResult = this.updateById(updateSpace);
        ThrowUtils.throwIf(!updateResult,
                ErrorCode.OPERATION_ERROR, "空间更新失败");

        // 8. 构建更新结果
        Map<String, Object> resultMap = new HashMap<>(8);
        resultMap.put("spaceId", spaceId);
        resultMap.put("updated", true);
        resultMap.put("oldSpaceLevel", oldLevelValue);
        resultMap.put("oldSpaceLevelText", oldLevelText);
        resultMap.put("newSpaceLevel", newLevelValue);
        resultMap.put("newSpaceLevelText", newLevelText);
        resultMap.put("updateTime", updateTime);
        if (newLevelEnum != null) {
            resultMap.put("message",
                    String.format("空间更新成功，级别从【%s】变更为【%s】", oldLevelText, newLevelText));
        } else {
            resultMap.put("message", "空间信息更新成功（级别未变更）");
        }

        // 9. 返回结果
        return resultMap;
    }

    // endregion

    // region 工具类

    /**
     * 自动填充空间(容量与数量)限额
     *
     * @param space 需要填充限额的空间实体对象，需包含以下关键信息：
     *              spaceLevel：空间级别
     *              maxSize：空间最大容量
     *              maxCount：空间最大图片数量
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            if (space.getMaxSize() == null) {
                long maxSize = spaceLevelEnum.getMaxSize();
                space.setMaxSize(maxSize);
            }
            if (space.getMaxCount() == null) {
                long maxCount = spaceLevelEnum.getMaxCount();
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * （Space）转换为（SpaceVO）
     *
     * @param space   数据库中的空间实体对象
     * @param request HTTP请求对象
     * @return SpaceVO 前端展示用的空间视图模
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long id = space.getId();
        if (id != null && id > 0) {
            User user = userService.getById(id);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    /**
     * （Page<Space>）转换为（Page<SpaceVO>）
     *
     * @param spacePage 数据库查询得到的空间实体分页对象
     * @param request   HTTP请求对象
     * @return Page<SpaceVO> 前端可直接渲染的空间VO分页对象
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        // 1. 初始化分页对象
        Page<SpaceVO> spaceVOPage = new Page<>(
                spacePage.getCurrent(),
                spacePage.getSize(),
                spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 2. 实体列表转换为VO列表
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());
        // 3. 获取查询用户信息
        // 批量收集用户 ID
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 构建ID到User的映射
        Map<Long, User> userIdUserMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        // 4. 为VO填充用户信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = userIdUserMap.get(userId);
            spaceVO.setUser(Optional.ofNullable(user)
                    .map(userService::getUserVO)
                    // 空用户处理
                    .orElseGet(() -> {
                        UserVO defaultUser = new UserVO();
                        defaultUser.setUserName("未知用户");
                        defaultUser.setId(-1L);
                        return defaultUser;
                    }));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 校验空间实体数据的合法性
     *
     * @param space 需要校验的空间实体对象，包含以下关键字段：
     *              spaceName：空间名称
     *              spaceLevel：空间级别
     * @param add   操作类型标识：
     *              true：表示为创建新空间操作，需校验必填字段
     *              false：表示为更新已有空间操作，仅校验非空字段的合法性
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        // 创建数据时校验
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName),
                    ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceLevel == null,
                    ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            ThrowUtils.throwIf(spaceType == null,
                    ErrorCode.PARAMS_ERROR, "空间类型不能为空");
        }
        // 修改数据时,如果要改空间级别
        ThrowUtils.throwIf(spaceLevel != null && spaceLevelEnum == null,
                ErrorCode.PARAMS_ERROR, "空间级别不存在");
        ThrowUtils.throwIf(StrUtil.isNotBlank(spaceName) && spaceName.length() > 30,
                ErrorCode.PARAMS_ERROR, "空间名称过长");
        ThrowUtils.throwIf(spaceType != null && spaceTypeEnum == null,
                ErrorCode.PARAMS_ERROR, "空间类型不存在");
    }

    /**
     * 校验用户对指定空间的操作权限
     *
     * @param loginUser 当前登录用户信息对象
     *                  需包含用户唯一标识（id），用于判断是否为空间所有者或管理员
     * @param space     待操作的空间对象
     *                  需包含空间所属用户标识（userId），用于与登录用户id匹配
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 仅本人或管理员可编辑
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
    // endregion


}




