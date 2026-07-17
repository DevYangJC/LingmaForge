package com.lingmaforge.backend.common.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 彻底解决大模型返回 List 变 Object（或单体元素）导致的反序列化报错。
 */
public class RobustListDeserializer extends JsonDeserializer<List<?>> implements ContextualDeserializer {

    private JavaType valueType;

    public RobustListDeserializer() {
    }

    public RobustListDeserializer(JavaType valueType) {
        this.valueType = valueType;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) throws JsonMappingException {
        JavaType type = property.getType();
        if (type.isCollectionLikeType()) {
            return new RobustListDeserializer(type.getContentType());
        }
        return new RobustListDeserializer(ctxt.constructType(Object.class));
    }

    @Override
    public List<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        List<Object> result = new ArrayList<>();

        if (node == null || node.isNull()) {
            return result;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                result.add(p.getCodec().treeToValue(item, valueType.getRawClass()));
            }
        } else if (node.isObject()) {
            // 如果大模型返回了对象而非数组（如：{"login": {...}, "register": {...}}）
            // 提取所有的 values 作为 List
            Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                JsonNode element = elements.next();
                if (element.isObject()) {
                    result.add(p.getCodec().treeToValue(element, valueType.getRawClass()));
                } else {
                    // 如果并不是对象字典，可能大模型直接返回了单个实体的对象，那就把它直接包装为List
                    result.add(p.getCodec().treeToValue(node, valueType.getRawClass()));
                    break;
                }
            }
        } else {
            // 字符串或其他基本类型单体值
            result.add(p.getCodec().treeToValue(node, valueType.getRawClass()));
        }

        return result;
    }
}
