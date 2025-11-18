package com.ye.yepicturebackend.api.imageSearch.sub;

import com.ye.yepicturebackend.exception.BusinessException;
import com.ye.yepicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百度以图搜图结果页
 */
@Slf4j
public class GetImageFirstUrlApi {

    /**
     * 从百度以图搜图结果页URL中提取
     *
     * @param url 百度以图搜图结果页的完整URL
     * @return 处理后的首图跳转链接（firstUrl），URL中的转义字符已替换（如"\\"转为"/"）
     */
    public static String getImageFirstUrl(String url) {
        try {
            // 1. 使用Jsoup发起HTTP GET请求，获取目标页面的HTML文档
            Document document = Jsoup.connect(url)
                    .timeout(5000)
                    .get();

            // 2. 从HTML文档中筛选所有<script>标签
            Elements scriptElements = document.getElementsByTag("script");

            // 3. 查找包含"firstUrl"字段的脚本内容
            for (Element script : scriptElements) {
                // 获取<script>标签内部的文本内容
                String scriptContent = script.html();

                // 跳过不包含"firstUrl"的脚本，减少无效匹配
                if (scriptContent.contains("\"firstUrl\"")) {
                    // 4. 正则表达式匹配"firstUrl"的值
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
                    Matcher matcher = pattern.matcher(scriptContent);

                    // 若匹配到结果
                    if (matcher.find()) {
                        String firstUrl = matcher.group(1);
                        // 5. 处理URL中的转义字符
                        firstUrl = firstUrl.replace("\\/", "/");
                        // 返回处理后的首图链接
                        return firstUrl;
                    }
                }
            }

            // 遍历所有脚本后仍未找到"firstUrl"，抛出业务异常
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未找到firstUrl字段，页面脚本中无匹配内容");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("从URL[{}]提取firstUrl失败", url, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败：" + e.getMessage());
        }
    }

    // 测试方法
    public static void main(String[] args) {
        String url = "https://graph.baidu.com/s?card_key=&ent" +
                "rance=GENERAL&extUiData%5BisLogoShow%5D=1&f=" +
                "all&isLogoShow=1&session_id=161566392294211451" +
                "95&sign=12602bffcfaad4a12d98c01760003244&tpl_from=pc";
        try {
            String imageFirstUrl = getImageFirstUrl(url);
            System.out.println("搜索成功，结果 URL：" + imageFirstUrl);
        } catch (Exception e) {
            System.out.println("搜索失败：" + e.getMessage());
        }
    }
}