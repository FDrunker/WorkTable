package com.ruoyi.web.controller.knowledge;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.CollectionView;
import com.ruoyi.knowledge.domain.DocumentChunkView;
import com.ruoyi.knowledge.domain.DocumentImportRequest;
import com.ruoyi.knowledge.domain.DocumentView;
import com.ruoyi.knowledge.service.ChromaCollectionService;
import com.ruoyi.knowledge.service.KnowledgeDocumentService;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeCollectionController extends BaseController
{
    private final ChromaCollectionService chromaService;

    private final KnowledgeDocumentService documentService;

    public KnowledgeCollectionController(ChromaCollectionService chromaService,
            KnowledgeDocumentService documentService)
    {
        this.chromaService = chromaService;
        this.documentService = documentService;
    }

    @PreAuthorize("@ss.hasPermi('knowledge:collection:list')")
    @GetMapping("/collection/list")
    public AjaxResult listCollections(@RequestParam(required = false) String name)
    {
        List<CollectionView> collections = chromaService.listCollections(name);
        return AjaxResult.success(collections);
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:list')")
    @GetMapping("/document/list")
    public TableDataInfo listDocuments(@RequestParam String collectionId,
            @RequestParam(required = false) String collectionName,
            @RequestParam(required = false) String documentName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        return documentService.list(collectionId, collectionName, documentName, pageNum, pageSize);
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:list')")
    @GetMapping("/document/{documentId}")
    public AjaxResult getDocument(@PathVariable String documentId)
    {
        return AjaxResult.success(documentService.getDocument(documentId));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:list')")
    @GetMapping("/document/{documentId}/chunks")
    public AjaxResult chunks(@PathVariable String documentId, @RequestParam String collectionId)
    {
        List<DocumentChunkView> chunks = documentService.chunks(collectionId, documentId);
        return AjaxResult.success(chunks);
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:add')")
    @Log(title = "知识库文档", businessType = BusinessType.INSERT)
    @PostMapping(value = "/document/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult importDocument(DocumentImportRequest request,
            @RequestPart(required = false) MultipartFile file)
    {
        return AjaxResult.success(documentService.importDocument(request, file, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:edit')")
    @Log(title = "知识库文档", businessType = BusinessType.UPDATE)
    @PutMapping(value = "/document/{documentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult updateDocument(@PathVariable String documentId, DocumentImportRequest request,
            @RequestPart(required = false) MultipartFile file)
    {
        return AjaxResult.success(documentService.updateDocument(documentId, request, file, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:remove')")
    @Log(title = "知识库文档", businessType = BusinessType.DELETE)
    @DeleteMapping("/document/{documentId}")
    public AjaxResult removeDocument(@PathVariable String documentId, @RequestParam String collectionId)
    {
        return toAjax(documentService.delete(collectionId, documentId));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:reindex')")
    @Log(title = "知识库文档重建", businessType = BusinessType.UPDATE)
    @PostMapping("/document/{documentId}/reindex")
    public AjaxResult reindex(@PathVariable String documentId, @RequestBody DocumentImportRequest request)
    {
        return AjaxResult.success(documentService.reindex(request.getCollectionId(), documentId,
                request.getModelId(), getUsername()));
    }
}
