-- 将历史 ai_chat_message.sources_json 压缩为知识片段引用。
-- 仅保留 record id 和 collection id，不保存正文、文档名、距离等内容。

update ai_chat_message m
set m.sources_json = (
  select json_arrayagg(json_object('id', jt.id, 'collectionId', jt.collection_id))
  from json_table(m.sources_json, '$[*]' columns (
    id varchar(255) path '$.id',
    collection_id varchar(255) path '$.collectionId'
  )) as jt
)
where json_valid(m.sources_json) = 1
  and json_type(m.sources_json) = 'ARRAY'
  and json_length(m.sources_json) > 0;
