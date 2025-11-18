package com.ye.yepicturebackend.model.vo;

import com.ye.yepicturebackend.model.entity.Space;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 空间信息
 */
@Data
public class SpaceVO implements Serializable {
    /**
     * id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;

    /**
     * 当前空间下图片的总大小
     */
    private Long totalSize;

    /**
     * 当前空间下的图片数量
     */
    private Long totalCount;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息
     */
    private UserVO user;

    /**
     * 权限列表
     */
    private List<String> permissionList = new ArrayList<>();

    private static final long serialVersionUID = -3043315378491381101L;

    /**
     * 封装类转对象
     * 此方法用于将SpaceVO视图对象转换为Space实体对象
     * <p>
     * 使用Spring的BeanUtils工具类进行属性拷贝
     *
     * @param spaceVO Space视图对象，包含前端传递的数据
     * @return Space 转换后的Space实体对象，如果输入为null则返回null
     */
    public static Space voToObj(SpaceVO spaceVO) {
        // 参数校验，如果输入对象为null，则直接返回null
        if (spaceVO == null) {
            return null;
        }
        // 创建Space实体对象
        Space space = new Space();
        // 使用BeanUtils工具类将spaceVO的属性值复制到space对象中
        BeanUtils.copyProperties(spaceVO, space);
        // 返回转换后的对象
        return space;
    }

    /**
     * 对象转封装类
     * 该方法用于将Space对象转换为SpaceVO视图对象
     * <p>
     * 使用Spring的BeanUtils工具类进行属性复制
     *
     * @param space 需要转换的Space实体对象
     * @return 转换后的SpaceVO视图对象，如果输入参数为null则返回null
     */
    public static SpaceVO objToVo(Space space) {
        // 检查输入参数是否为null，如果是则直接返回null
        if (space == null) {
            return null;
        }
        // 创建SpaceVO对象实例
        SpaceVO spaceVO = new SpaceVO();
    // 使用BeanUtils工具类将space对象的属性复制到spaceVO对象中
        BeanUtils.copyProperties(space, spaceVO);
    // 返回转换后的VO对象
        return spaceVO;
    }
}
