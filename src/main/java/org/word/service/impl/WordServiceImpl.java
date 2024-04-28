package org.word.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.word.model.Request;
import org.word.model.Response;
import org.word.model.Table;
import org.word.service.WordService;
import org.word.utils.JsonUtils;
import org.word.utils.MenuUtils;

import java.io.IOException;
import java.util.*;

/**
 * @Author XiuYin.Cui
 * @Date 2018/1/12
 **/
@Slf4j
@Service
public class WordServiceImpl implements WordService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${swagger.url}")
    private String swaggerUrl;

    @Override
    public List<Table> tableList(String jsonUrl) {
        jsonUrl = Optional.ofNullable(jsonUrl).orElse(swaggerUrl);
        List<Table> result = new ArrayList<>();
        Set<String> titleSet = new HashSet<String>();
        try {
            String jsonStr = restTemplate.getForObject(jsonUrl, String.class);
            // convert JSON string to Map
            Map<String, Object> map = JsonUtils.readValue(jsonStr, HashMap.class);
            //解析paths
            Map<String, LinkedHashMap> paths = (LinkedHashMap) map.get("paths");
            if (paths != null) {
                Iterator<Map.Entry<String, LinkedHashMap>> it = paths.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, LinkedHashMap> path = it.next();

                    Iterator<Map.Entry<String, LinkedHashMap>> it2 = path.getValue().entrySet().iterator();
                    // 1.请求路径
                    String url = path.getKey();
                    // 2.请求方式，类似为 get,post,delete,put 这样
                    String requestType = StringUtils.join(path.getValue().keySet(), ",");
                    //不管有几种请求方式，都只解析第一种
                    Map.Entry<String, LinkedHashMap> firstRequest = it2.next();
                    Map<String, Object> content = firstRequest.getValue();
                    // 4. 大标题（类说明） 默认未swagger title
                    String title = ((LinkedHashMap) map.get("info")).get("title").toString();
                    try {
                        title = String.valueOf(((List) content.get("tags")).get(0));
                    } catch (Exception e) {
                        log.error("------------------- 无tags  -------------");
                    }
                    // 5.小标题 （方法说明）
                    String tag = String.valueOf(content.get("summary"));
                    // 6.接口描述
                    String description = String.valueOf(content.get("description"));
                    if (description== "null") {
                        description = tag;
                    }
                    // 7.请求参数格式，类似于 multipart/form-data
                    String requestForm = "";
                    List<String> consumes = (List) content.get("consumes");
                    if (consumes != null && consumes.size() > 0) {
                        requestForm = StringUtils.join(consumes, ",");
                    }
                    // 8.返回参数格式，类似于 application/json
                    String responseForm = "";
                    List<String> produces = (List) content.get("produces");
                    if (produces != null && produces.size() > 0) {
                        responseForm = StringUtils.join(produces, ",");
                    }
                    // 9. 请求体
                    List<Request> requestList = new ArrayList<>();
                    List<LinkedHashMap> parameters = (ArrayList) content.get("parameters");
//                    if (!CollectionUtils.isEmpty(parameters)) {
//                        for (Map<String, Object> param : parameters) {
//                            Request request = new Request();
//                            request.setName(String.valueOf(param.get("name")));
//                            Object in = param.get("in");
//                            if (in != null && "body".equals(in)) {
//                                request.setType(String.valueOf(in));
//                                Map<String, Object> schema = (Map) param.get("schema");
//                                Object ref = schema.get("$ref");
//                                // 数组情况另外处理
//                                if (schema.get("type") != null && "array".equals(schema.get("type"))) {
//                                    ref = ((Map) schema.get("items")).get("$ref");
//                                }
//                                request.setParamType(ref == null ? "{}" : ref.toString());
//                            } else {
//                                request.setType(param.get("type") == null ? "Object" : param.get("type").toString());
//                                request.setParamType(String.valueOf(in));
//                            }
//                            if (param.get("required") != null) {
//                                request.setRequire((Boolean) param.get("required"));
//                            } else {
//                                request.setRequire(false);
//                            }
//                            request.setRemark(String.valueOf(param.get("description")));
//                            requestList.add(request);
//                        }
//                    }
                    // 处理 "parameters":
                    if (parameters != null) {
                        for (int i = 0; i < parameters.size(); i++) {
                            // 没有schema ,
                            if (parameters != null && parameters.get(i) != null && ((Map) parameters.get(i).get("schema")) == null) {
                                Map<String, Object> tempP = parameters.get(i);
                                Request request = new Request();
                                String desc = String.valueOf(tempP.get("description"));
                                String type = String.valueOf(tempP.get("type")); // 默认字符串
                                if (tempP.get("type") == null || tempP.get("type").equals("null")) {
                                    type = "string";
                                }
                                if (tempP.get("in").equals("path")) {
                                    desc += "(此参数需要拼接到请求路径 path 上)";
                                    type = "string";
                                }
                                if (tempP.get("in").equals("formData")) {
                                    desc += "(此参数需要使用FormData的形式传递)";
                                }
                                request.setName(String.valueOf(tempP.get("name")));
                                request.setType(type);
                                String reqi = String.valueOf(tempP.get("require"));
                                if (reqi == null || reqi.equals("null")) {
                                    reqi = "string";
                                }
                                request.setRequire(reqi.equals("true"));
                                request.setParamType(type);
                                request.setRemark(desc);
                                requestList.add(request);
                            }
                            // 获取的是层的数据
                            if (parameters != null && parameters.get(i) != null && ((Map) parameters.get(i).get("schema")) != null) {
                                String ref = (String) ((Map) parameters.get(i).get("schema")).get("$ref");
                                if (ref != null) {
                                    Map<String, Object> parameters2 = this.getParams(ref, map);
                                    if (parameters != null) {
                                        if (parameters2 == null) {
                                            Request request = new Request();
                                            request.setName("JSON");
                                            request.setType("string");
                                            request.setRequire(false);
                                            request.setParamType("string");
                                            request.setRemark("JSON格式或者其他");
                                            requestList.add(request);
                                        } else {
                                            List<String> requireList = this.getRequire(ref, map);
                                            for (String key : parameters2.keySet()) {
                                                Object obj = parameters2.get(key);
                                                Request request = new Request();
                                                request.setName(key);
                                                String type = String.valueOf(((Map) obj).get("type"));
                                                if (type.equals("null")) {
                                                    type = "string";
                                                }
                                                request.setType(type);
                                                if (requireList != null && requireList.contains(key)) {
                                                    request.setRequire(true);
                                                } else {
                                                    request.setRequire(false);
                                                }
                                                request.setParamType(type);
                                                request.setRemark(String.valueOf(((Map) obj).get("description")));
                                                requestList.add(request);
                                            }
                                        }
                                    }
                                } else {
                                    // 处理有 schema 无ref的情况
//                                    {
//                                        "name": "state",
//                                            "in": "query",
//                                            "description": "状态（active:激活 suspended:挂起）",
//                                            "required": true,
//                                            "schema": {
//                                        "type": "string"
//                                    }
                                    Map<String, Object> reqP = parameters.get(i);
                                    Request request = new Request();
                                    request.setName((String) reqP.get("name"));
                                    request.setRequire((Boolean) reqP.get("required"));
                                    String type = String.valueOf(((Map) reqP.get("schema")).get("type"));
                                    if (type.equals("null")) {
                                        type = "string";
                                    }
                                    request.setType(type);
                                    request.setParamType(type);
                                    request.setRemark((String) reqP.get("description"));
                                    requestList.add(request);
                                }
                            }
                        }
                    }

                    // 处理 "requestBody": {
                    Map<String, LinkedHashMap> requestBody = (LinkedHashMap) content.get("requestBody");
                    if (requestBody != null) {
                        Map<String, LinkedHashMap> contett = (LinkedHashMap) requestBody.get("content");
                        Map<String, LinkedHashMap> applJ = (LinkedHashMap) contett.get("application/json");
                        Map<String, String> schema = applJ != null ? (LinkedHashMap) applJ.get("schema") : null;
                        String ref = schema != null ? schema.get("$ref") : null;
                        if (ref != null) {
                            Map<String, Object> parameters2 = this.getParams(ref, map);
                            if (parameters2 == null) {
                                Request request = new Request();
                                request.setName("JSON");
                                request.setType("string");
                                request.setRequire(false);
                                request.setParamType("string");
                                request.setRemark("JSON格式或者其他");
                                requestList.add(request);
                            } else {
                                List<String> requireList = this.getRequire(ref, map);
                                for (String key : parameters2.keySet()) {
                                    Object obj = parameters2.get(key);
                                    Request request = new Request();
                                    request.setName(key);
                                    String type = String.valueOf(((Map) obj).get("type"));
                                    if (type.equals("null")) {
                                        type = "string";
                                    }
                                    request.setType(type);
                                    if (requireList != null && requireList.contains(key)) {
                                        request.setRequire(true);
                                    } else {
                                        request.setRequire(false);
                                    }
                                    request.setParamType(type);
                                    request.setRemark(String.valueOf(((Map) obj).get("description")));
                                    requestList.add(request);
                                }
                            }
                        }
                    }

                    // 10.返回体
                    List<Response> responseList = new ArrayList<>();
                    Map<String, Object> responses = (LinkedHashMap) content.get("responses");
                    Iterator<Map.Entry<String, Object>> it3 = null;
                    if(responses != null) {
                        it3 = responses.entrySet().iterator();
                    }
                    while (it3 != null && it3.hasNext()) {
                        Response response = new Response();
                        Map.Entry<String, Object> entry = it3.next();
                        // 状态码 200 201 401 403 404 这样
                        response.setName(entry.getKey());
                        LinkedHashMap<String, Object> statusCodeInfo = (LinkedHashMap) entry.getValue();
                        response.setDescription(String.valueOf(statusCodeInfo.get("description")));
                        response.setRemark(String.valueOf(statusCodeInfo.get("description")));
                        responseList.add(response);
                    }

                    // 保存出现的tag
                    titleSet.add(title);
                    //封装Table
                    Table table = new Table();
                    //是否添加为菜单
//                    if (MenuUtils.isMenu(title)) {
                        table.setTitle(title);
//                    }
                    table.setUrl(url);
                    table.setTag(tag);
                    table.setDescription(description);
                    table.setRequestForm(requestForm);
                    table.setResponseForm(responseForm);
                    table.setRequestType(requestType);
                    table.setResponseList(responseList);
                    table.setRequestParam(this.format(JsonUtils.writeJsonStr(buildParamMap(requestList, map))));
                    for (Request request : requestList) {
                        request.setParamType(request.getParamType().replaceAll("#/definitions/", ""));
                    }
                    table.setRequestList(requestList);
                    // 取出来状态是200时的返回值
                    Object obj = null;
                    if( responses != null) {
                        obj =  responses.get("200");
                    }
                    if (obj == null) {
                        table.setResponseParam("");
                        result.add(table);
                        continue;
                    }
                    // 两个结构
//                    "responses": {
//                        "200": {
//                            "description": "OK",
//                                    "content": {
//                                "*/*": {
//                                    "schema": {
//                                        "$ref": "#/components/schemas/RString"
//                                    }
//                                }
//                            }
//                        }
//                    }
//                    "responses":{
//                        "200": {
//                              "$ref":"#/components/schemas/RString"
//                          }
//                    }
                    Object schema = ((Map) obj).get("schema");
                    if(schema == null) {
                        schema = obj;
                    }

                    if (schema == null  ) {
                        table.setResponseParam("");
                        result.add(table);
                        continue;
                    }

                    if (((Map) schema).get("$ref") != null) {
                        //非数组类型返回值
                        String ref = (String) ((Map) schema).get("$ref");
                        //解析swagger2 ref链接
                        ObjectNode objectNode = parseRef(ref, map);
                        table.setResponseParam(this.format(objectNode.toString()));
                        result.add(table);
                        continue;
                    }
                    if(((Map) schema).get("content") != null){
                        Object respContent =((Map) schema).get("content");
                        Object xing = ((Map) respContent).get("*/*");
                        Object tempSchema = ((Map) xing).get("schema");
                        String ref = (String) ((Map) tempSchema).get("$ref");
                        //解析swagger2 ref链接
                        ObjectNode objectNode = parseRef(ref, map);
                        table.setResponseParam(this.format(objectNode.toString()));
                        result.add(table);
                        continue;
                    }

                    Object items = ((Map) schema).get("items");
                    if (items != null && ((Map) items).get("$ref") != null) {
                        //数组类型返回值
                        String ref = (String) ((Map) items).get("$ref");
                        //解析swagger2 ref链接
                        ObjectNode objectNode = parseRef(ref, map);
                        ArrayNode arrayNode = JsonUtils.createArrayNode();
                        arrayNode.add(objectNode);
                        table.setResponseParam(this.format(arrayNode.toString()));
                        result.add(table);
                        continue;
                    }
                    result.add(table);

                }
            }
        } catch (Exception e) {
            log.error("parse error", e);
        }
//        处理排序问题 解决同一个tag 下的问题
        List<Table> sortResult = new ArrayList<>();
        for ( String til : titleSet) {
            for (int i = 0; i < result.size(); i++) {
                Table tempT = result.get(i);
                if(tempT.getTitle().equals(til)) {
                    sortResult.add(tempT);
                }
            }
        }
        for (int i = 0; i < sortResult.size(); i++) {
            String tempT = sortResult.get(i).getTitle();
            if (MenuUtils.isMenu(tempT)) {
                sortResult.get(i).setTitle(tempT);
            } else {
                sortResult.get(i).setTitle("");
            }
        }

        return sortResult;
    }

    private Map<String, Object> getParams(String ref, Map<String, Object> map) {
        Map<String, Object> tmpMap = null;
        if (StringUtils.isNotEmpty(ref) && ref.startsWith("#")) {
            String[] refs = ref.split("/");
            tmpMap = map;
            //取出ref最后一个参数 start
            for (String tmp : refs) {
                if (!"#".equals(tmp)) {
                    tmpMap = (Map<String, Object>) tmpMap.get(tmp);
                }
            }
        }
        if (tmpMap == null) {
            return null;
        }
        return (Map<String, Object>) tmpMap.get("properties");
    }

    private List<String> getRequire(String ref, Map<String, Object> map) {
        Map<String, Object> tmpMap = null;
        if (StringUtils.isNotEmpty(ref) && ref.startsWith("#")) {
            String[] refs = ref.split("/");
            tmpMap = map;
            //取出ref最后一个参数 start
            for (String tmp : refs) {
                if (!"#".equals(tmp)) {
                    tmpMap = (Map<String, Object>) tmpMap.get(tmp);
                }
            }
        }
        return (List<String>) tmpMap.get("required");
    }


    /**
     * 从map中解析出指定的ref
     *
     * @param ref ref链接 例如："#/definitions/PageInfoBT«Customer»"
     * @param map 是整个swagger json转成map对象
     * @return
     * @author fpzhan
     */
    private ObjectNode parseRef(String ref, Map<String, Object> map) {
        ObjectNode objectNode = JsonUtils.createObjectNode();
        if (StringUtils.isNotEmpty(ref) && ref.startsWith("#")) {
            String[] refs = ref.split("/");

            Map<String, Object> tmpMap = map;
            //取出ref最后一个参数 start
            for (String tmp : refs) {
                if (!"#".equals(tmp)) {
                    tmpMap = (Map<String, Object>) tmpMap.get(tmp);
                }
            }
            //取出ref最后一个参数 end
            //取出参数
            if (tmpMap == null) {
                return objectNode;
            }
            // 如果是返回值  responses
            Object properties = null;
            if (refs[1].equals("responses")) {
                tmpMap = (Map<String, Object>) tmpMap.get("schema");
                properties = tmpMap.get("properties");
            } else {
                properties = tmpMap.get("properties");
            }
            if (properties == null) {
                return objectNode;
            }
            Map<String, Object> propertiesMap = (Map<String, Object>) properties;
            Set<String> keys = propertiesMap.keySet();
            //遍历key
            for (String key : keys) {
                Map<String, Object> keyMap = (Map) propertiesMap.get(key);
                if ("array".equals(keyMap.get("type"))) {
                    //数组的处理方式
                    String sonRef = (String) ((Map) keyMap.get("items")).get("$ref");
                    //对象自包含，跳过解析
                    if (ref.equals(sonRef)) {
                        continue;
                    }
                    JsonNode jsonNode = parseRef(sonRef, map);
                    ArrayNode arrayNode = JsonUtils.createArrayNode();
                    arrayNode.add(jsonNode);
                    objectNode.set(key, arrayNode);
                } else if (keyMap.get("$ref") != null) {
                    //对象的处理方式
                    String sonRef = (String) keyMap.get("$ref");
                    //对象自包含，跳过解析
                    if (ref.equals(sonRef)) {
                        continue;
                    }
                    ObjectNode object = parseRef(sonRef, map);
                    objectNode.set(key, object);
                } else {
                    //其他参数的处理方式，string、int
                    String str = "";
                    if (keyMap.get("description") != null) {
                        str = str + keyMap.get("description");
                    }
                    if (keyMap.get("format") != null) {
                        str = str + String.format("格式为(%s)", keyMap.get("format"));
                    }
                    objectNode.put(key, str);
                }
            }
        }
        return objectNode;
    }


    /**
     * 得到格式化json数据  退格用\t 换行用\r
     */
    private String format(String jsonStr) {
        int level = 0;
        StringBuffer jsonForMatStr = new StringBuffer();
        for(int i=0;i<jsonStr.length();i++){
            char c = jsonStr.charAt(i);
            if(level>0&&'\n'==jsonForMatStr.charAt(jsonForMatStr.length()-1)){
                jsonForMatStr.append(this.getLevelStr(level));
            }
            switch (c) {
                case '{':
                case '[':
                    jsonForMatStr.append(c+"<br/>\n");
                    level++;
                    jsonForMatStr.append(this.getLevelStr(level));
                    break;
                case ',':
                    char d = jsonStr.charAt(i-1);
                    if(d == '"' || d == ']' || d == '}' || d >=0 || d<=9){
                        if (!Character.isWhitespace(c)) {
                            jsonForMatStr.append(c+"<br/>\n");
                            jsonForMatStr.append(this.getLevelStr(level));
                        }
                    } else {
                        jsonForMatStr.append(c);
                    }
                    break;
                case '}':
                case ']':
                    jsonForMatStr.append("<br/>\n");
                    level--;
                    jsonForMatStr.append(this.getLevelStr(level));
                    jsonForMatStr.append(c);
                    break;
                default:
                    jsonForMatStr.append(c);
                    break;
            }
        }
        return jsonForMatStr.toString();
    }

    private String getLevelStr(int level){
        StringBuffer levelStr = new StringBuffer();
        for(int levelI = 0;levelI<level ; levelI++){
            levelStr.append("&nbsp;&nbsp;&nbsp;&nbsp;");
//            levelStr.append("<span style='width:21px;text-indent: 21px;display: inline-block;'></span>\t");
        }
        return levelStr.toString();
    }


    /**
     * 封装post请求体
     *
     * @param list
     * @param map
     * @return
     */
    private Map<String, Object> buildParamMap(List<Request> list, Map<String, Object> map) throws IOException {
        Map<String, Object> paramMap = new HashMap<>(8);
        if (list != null && list.size() > 0) {
            for (Request request : list) {
                String name = request.getName();
                String type = request.getType();
                switch (type) {
                    case "string":
                        paramMap.put(name, "string");
                        break;
                    case "integer":
                        paramMap.put(name, 0);
                        break;
                    case "number":
                        paramMap.put(name, 0.0);
                        break;
                    case "boolean":
                        paramMap.put(name, true);
                        break;
                    case "body":
                        String paramType = request.getParamType();
                        ObjectNode objectNode = parseRef(paramType, map);
                        paramMap = JsonUtils.readValue(objectNode.toString(), Map.class);
                        break;
                    default:
                        paramMap.put(name, null);
                        break;
                }
            }
        }
        return paramMap;
    }
}
