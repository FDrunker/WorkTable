-- 知识库正文改为本地临时文件暂存，不再保存到 MySQL
alter table ai_knowledge_document drop column content;
