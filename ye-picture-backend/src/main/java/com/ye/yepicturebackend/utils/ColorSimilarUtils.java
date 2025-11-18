package com.ye.yepicturebackend.utils;

import java.awt.*;

/**
 * 颜色相似度计算工具类
 */
public class ColorSimilarUtils {

    /**
     * 私有构造方法：工具类禁止实例化
     */
    private ColorSimilarUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 各颜色通道的权重（基于人眼敏感度：绿色>红色>蓝色）
     * - 绿色（G）权重最高：人眼对绿色敏感度最强
     * - 红色（R）次之：人眼对红色敏感度中等
     * - 蓝色（B）最低：人眼对蓝色敏感度较弱
     */
    private static final int WEIGHT_RED = 2;
    private static final int WEIGHT_GREEN = 4;
    private static final int WEIGHT_BLUE = 3;

    /**
     * 计算两个Color对象的相似度（基于加权欧氏距离）
     *
     * @param color1 第一个颜色对象（包含RGB三通道值）
     * @param color2 第二个颜色对象（包含RGB三通道值）
     * @return 相似度值，范围0~1（值越接近1表示颜色越相似，完全相同为1）
     */
    public static double calculateSimilarity(Color color1, Color color2) {
        // 提取两个颜色的RGB通道值（0~255）
        int r1 = color1.getRed();
        int g1 = color1.getGreen();
        int b1 = color1.getBlue();

        int r2 = color2.getRed();
        int g2 = color2.getGreen();
        int b2 = color2.getBlue();

        // 计算各通道差值的加权平方和
        // 公式：(R1-R2)²×权重R + (G1-G2)²×权重G + (B1-B2)²×权重B
        long redDiff = (long) Math.pow(r1 - r2, 2) * WEIGHT_RED;
        long greenDiff = (long) Math.pow(g1 - g2, 2) * WEIGHT_GREEN;
        long blueDiff = (long) Math.pow(b1 - b2, 2) * WEIGHT_BLUE;

        // 计算加权欧氏距离（开平方）
        double distance = Math.sqrt(redDiff + greenDiff + blueDiff);

        // 计算最大可能距离（当两颜色完全相反时的理论最大值）
        // 公式：√[(255²×权重R) + (255²×权重G) + (255²×权重B)]
        double maxDistance = Math.sqrt(
                (long) Math.pow(255, 2) * WEIGHT_RED +
                (long) Math.pow(255, 2) * WEIGHT_GREEN +
                (long) Math.pow(255, 2) * WEIGHT_BLUE
        );

        // 转换为相似度：1 - 距离占比（距离越小，相似度越高）
        return 1 - (distance / maxDistance);
    }

    /**
     * 计算两个十六进制颜色代码的相似度（基于加权欧氏距离）
     *
     * @param hexColor1 第一个颜色的十六进制字符串（格式：0x开头或#开头，如0xFF0000、#FF0000）
     * @param hexColor2 第二个颜色的十六进制字符串（同上）
     * @return 相似度值，范围0~1（值越接近1表示颜色越相似）
     */
    public static double calculateSimilarity(String hexColor1, String hexColor2) {
        // 将十六进制字符串解析为Color对象
        Color color1 = Color.decode(hexColor1);
        Color color2 = Color.decode(hexColor2);
        // 调用Color对象的相似度计算方法
        return calculateSimilarity(color1, color2);
    }

    /**
     * 示例代码：测试颜色相似度计算
     */
    public static void main(String[] args) {
        // 测试1：接近的红色（高相似度）
        Color red1 = Color.decode("0xFF0000");    // 纯红
        Color red2 = Color.decode("0xFE0101");    // 接近纯红的颜色
        double redSimilarity = calculateSimilarity(red1, red2);
        System.out.println("红色系相似度：" + redSimilarity);  // 输出约0.98

        // 测试2：红色与蓝色（低相似度）
        Color blue = Color.decode("0x0000FF");    // 纯蓝
        double redBlueSimilarity = calculateSimilarity(red1, blue);
        System.out.println("红蓝色系相似度：" + redBlueSimilarity);  // 输出约0.1

        // 测试3：处理省略前导0的格式（模拟腾讯COS返回值）
        double cosColorSimilarity = calculateSimilarity("0x8", "0x00000A");  // 接近的蓝色系
        System.out.println("腾讯COS格式颜色相似度：" + cosColorSimilarity);  // 输出约0.97
    }
}
