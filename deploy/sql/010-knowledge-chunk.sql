-- 006 AI 知识库分块向量表（只执行一次）
-- 适用于已经执行过 005 的现有开发库。
-- embedding 存 JSON 数组（OpenAI 兼容接口返回的 float 列表），余弦相似度在应用层计算。

USE farm_live;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  char_count INT NOT NULL DEFAULT 0,
  embedding MEDIUMTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_chunk_doc FOREIGN KEY (doc_id) REFERENCES knowledge_doc(id) ON DELETE CASCADE,
  INDEX idx_chunk_doc (doc_id, chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分块与向量';

SELECT 'knowledge_chunk created' AS result;
