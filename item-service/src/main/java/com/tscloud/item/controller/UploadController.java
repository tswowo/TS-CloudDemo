package com.tscloud.item.controller;

import com.tscloud.item.utils.OssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Api(tags = "文件上传接口")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final OssUtil ossUtil;

    @ApiOperation("图片上传（商户端，返回可访问 URL）")
    @PostMapping
    public String uploadImage(@RequestParam("file") MultipartFile file) {
        return ossUtil.upload(file);
    }
}
