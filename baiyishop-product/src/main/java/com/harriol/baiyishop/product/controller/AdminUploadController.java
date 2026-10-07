package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.product.storage.FileStorageService;
import com.harriol.baiyishop.product.storage.StoredFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台图片上传（REQ-203、REQ-401）。
 * <p>商品主图 / 图集与首页轮播图都走这个接口：上传成功后返回可直接入库的图片地址。
 */
@RestController
@RequestMapping("/api/v1/admin/uploads")
@RequiresRole({"SUPER_ADMIN", "OPERATOR"})
public class AdminUploadController {

    private final FileStorageService fileStorageService;

    public AdminUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * 上传图片。
     *
     * @param file  图片文件（jpg / png / webp / gif，≤5MB）
     * @param scene 业务场景，决定桶内目录：`product`（商品图，默认）/ `banner`（首页轮播）
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<StoredFile> uploadImage(@RequestPart("file") MultipartFile file,
                                         @RequestPart(value = "scene", required = false) String scene) {
        String folder = "banner".equalsIgnoreCase(scene) ? "banner" : "product";
        return Result.ok(fileStorageService.uploadImage(file, folder));
    }
}
