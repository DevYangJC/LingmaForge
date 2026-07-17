package com.lingmaforge.backend.common.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 兼容处理大模型可能返回的字符串数组或对象数组。
 * 例如：大模型可能返回 ["SearchBar"] 或 [{"name": "SearchBar"}]
 */
public class StringOrObjectListDeserializer extends JsonDeserializer<List<String>> {
    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    result.add(item.asText());
                } else if (item.isObject()) {
                    if (item.has("name")) {
                        result.add(item.get("name").asText());
                    } else if (item.has("description")) {
                        result.add(item.get("description").asText());
                    } else if (item.has("feature")) {
                        result.add(item.get("feature").asText());
                    } else {
                        result.add(item.toString());
                    }
                } else {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }
}
