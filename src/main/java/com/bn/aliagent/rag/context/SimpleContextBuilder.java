package com.bn.aliagent.rag.context;

import com.bn.aliagent.rag.model.RagChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 简单上下文构建器 —— 拼接 + 去重 + 硬截断
 *
 * <p>一期最简实现：</p>
 * <ol>
 *   <li>按片段顺序拼接 content</li>
 *   <li>基于 content.hashCode() 去重</li>
 *   <li>超出字符数限制时硬截断</li>
 * </ol>
 *
 * <p>二期升级：OptimizedContextBuilder（智能压缩 + Token Budget 控制）</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public class SimpleContextBuilder implements ContextBuilder {

    /** 片段之间的分隔符 */
    private static final String CHUNK_SEPARATOR = "\n---\n";

    @Override
    public String build(String query, List<RagChunk> chunks, int maxTokens) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        Set<Integer> seen = new HashSet<>();
        StringBuilder sb = new StringBuilder();

        for (RagChunk chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }

            // 基于内容哈希去重
            int hash = chunk.getContent().hashCode();
            if (!seen.add(hash)) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append(CHUNK_SEPARATOR);
            }
            sb.append(chunk.getContent());
        }

        String result = sb.toString().trim();

        // 硬截断：一期按字符数截断（二期升级为 Token-aware 截断）
        if (result.length() > maxTokens) {
            result = result.substring(0, maxTokens);
        }

        return result;
    }
}
