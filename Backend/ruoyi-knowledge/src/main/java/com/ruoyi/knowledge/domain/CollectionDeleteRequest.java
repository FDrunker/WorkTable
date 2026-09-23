package com.ruoyi.knowledge.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 删除 Chroma 集合请求。
 */
public class CollectionDeleteRequest
{
    private List<String> collectionIds = new ArrayList<>();

    public List<String> getCollectionIds()
    {
        return collectionIds;
    }

    public void setCollectionIds(List<String> collectionIds)
    {
        this.collectionIds = collectionIds;
    }
}
